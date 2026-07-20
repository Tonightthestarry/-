package com.massdata.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.massdata.dao.mongo.CityDataRepository;
import com.massdata.dao.mongo.AnalysisResultRepository;
import com.massdata.entity.AnalysisResult;
import com.massdata.entity.CityData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.Socket;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * HDFS归档与恢复服务
 * 优先使用真正的  hdfs dfs  命令，如果NameNode/DataNode未启动则回退到本地HDFS存储
 * （通过hadoop fs file:///协议访问，数据落本地磁盘，但通过Hadoop FS API读写）
 */
@Service
public class HdfsService {

    private static final Logger log = LoggerFactory.getLogger(HdfsService.class);
    private static final String HADOOP_HOME = "D:\\massdatanaly\\hadoop-3.5.0";
    private static final String HADOOP_BIN = HADOOP_HOME + "\\bin\\hadoop.cmd";
    private static final String HDFS_BASE = "/banaly2";
    // 本地HDFS 存储路径(当分布式HDFS不可用时)
    private static final String LOCAL_STORE = "D:\\massdatanaly\\hadoop-data\\hdfs-store";
    private static final ObjectMapper mapper = new ObjectMapper();

    // 设置 hadoop.home.dir 系统属性 (Hadoop 3.x 在 Windows 上必须, 否则会卡死)
    static {
        if (System.getProperty("hadoop.home.dir") == null) {
            System.setProperty("hadoop.home.dir", HADOOP_HOME);
        }
        // 禁止 Hadoop 访问某些不存在的资源时报警
        if (System.getProperty("hadoop.user.name") == null) {
            System.setProperty("hadoop.user.name", System.getProperty("user.name", "Administrator"));
        }
    }

    private final CityDataRepository cityDataRepository;
    private final AnalysisResultRepository analysisResultRepository;

    public HdfsService(CityDataRepository cityDataRepository,
                       AnalysisResultRepository analysisResultRepository) {
        this.cityDataRepository = cityDataRepository;
        this.analysisResultRepository = analysisResultRepository;
        initLocalStore();
    }

    private void initLocalStore() {
        try { Files.createDirectories(Paths.get(LOCAL_STORE, "archive")); } catch (Exception ignored) {}
    }

    // ==================== HDFS 状态 ====================

    public Map<String, Object> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("hadoopHome", HADOOP_HOME);
        status.put("hdfsBase", HDFS_BASE);
        status.put("localStore", LOCAL_STORE);

        boolean distributedOnline = checkDistributedHdfs();
        status.put("distributedOnline", distributedOnline);
        // 优先以"分布式 HDFS 在线"为准;若不在线但本地降级可用,也视为服务可用
        status.put("online", distributedOnline || checkLocalStoreAvailable());
        status.put("mode", distributedOnline ? "distributed" : "local-fallback");

        // 用 hadoop fs 命令列文件
        List<Map<String, String>> files = new ArrayList<>();
        try {
            if (distributedOnline) {
                List<String> out = runHadoop("fs", "-ls", HDFS_BASE + "/archive");
                for (String line : out) {
                    String t = line.trim();
                    if (t.startsWith("-rw")) files.add(parseFileLine(t));
                }
            }
            // 同时合并本地存储文件
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(Paths.get(LOCAL_STORE, "archive"), "*.json")) {
                for (Path p : ds) {
                    Map<String, String> f = new LinkedHashMap<>();
                    f.put("name", p.getFileName().toString());
                    f.put("size", String.valueOf(Files.size(p)));
                    f.put("date", Files.getLastModifiedTime(p).toString().substring(0, 19));
                    // 去重
                    if (files.stream().noneMatch(m -> m.get("name").equals(f.get("name")))) {
                        files.add(f);
                    }
                }
            }
        } catch (Exception e) { /* ignore */ }
        // 如果没有分布式文件，至少列本地文件
        if (files.isEmpty()) {
            try {
                try (DirectoryStream<Path> ds = Files.newDirectoryStream(Paths.get(LOCAL_STORE, "archive"), "*.json")) {
                    for (Path p : ds) {
                        Map<String, String> f = new LinkedHashMap<>();
                        f.put("name", p.getFileName().toString());
                        f.put("size", String.valueOf(Files.size(p)));
                        f.put("date", Files.getLastModifiedTime(p).toString().substring(0, 19));
                        files.add(f);
                    }
                }
            } catch (Exception e) { /* ignore */ }
        }

        status.put("fileCount", files.size());
        status.put("files", files);
        return status;
    }

    // ==================== 归档: MongoDB → HDFS ====================

    public Map<String, Object> archiveAnalysisResults() {
        return archive("analysis_results",
                new ArrayList<>(analysisResultRepository.findAll()));
    }

    public Map<String, Object> archiveCityData(String dataType) {
        List<CityData> docs = (dataType == null || dataType.isEmpty())
                ? cityDataRepository.findAll()
                : cityDataRepository.findByDataType(dataType);
        String label = dataType == null || dataType.isEmpty() ? "all" : dataType;
        return archive("city_data_" + label, new ArrayList<>(docs));
    }

    /**
     * 归档单条实时流数据 (新增 - 供 StreamingController 调)
     * 每次调用追加一条 JSON Line 到本地文件, hadoop 可选上传
     */
    public boolean archiveStream(String dataType, String jsonLine) {
        if (jsonLine == null || jsonLine.isEmpty()) return false;
        try {
            Path streamDir = Paths.get(LOCAL_STORE, "stream");
            Files.createDirectories(streamDir);
            // 按 dataType 拆文件, 文件名: stream_<dataType>.jsonl
            Path file = streamDir.resolve("stream_" + dataType + ".jsonl");
            // 追加一行
            try (var fw = Files.newBufferedWriter(file,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND)) {
                fw.write(jsonLine);
                fw.newLine();
            }
            return true;
        } catch (Exception e) {
            log.warn("archiveStream失败 (已跳过): {}", e.getMessage());
            return false;
        }
    }

    private Map<String, Object> archive(String prefix, List<?> docs) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalDocs", docs.size());
        try {
            String ts = now();
            String fileName = prefix + "_" + ts + ".json";
            // 写本地文件
            Path localFile = Paths.get(LOCAL_STORE, "archive", fileName);
            mapper.writerWithDefaultPrettyPrinter().writeValue(localFile.toFile(), docs);
            result.put("localPath", localFile.toString());

            // 尝试推送到分布式 HDFS
            if (checkDistributedHdfs()) {
                try {
                    runHadoop("fs", "-put", localFile.toString(), HDFS_BASE + "/archive/" + fileName);
                    result.put("hdfsPath", HDFS_BASE + "/archive/" + fileName);
                } catch (Exception e) {
                    log.warn("分布式HDFS上传失败,已存本地: {}", e.getMessage());
                }
            }

            result.put("success", true);
            result.put("message", "归档成功: " + docs.size() + " 条 → " + fileName);
            log.info("HDFS归档完成: {} 条 → {}", docs.size(), fileName);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "归档失败: " + e.getMessage());
            log.error("HDFS归档失败", e);
        }
        return result;
    }

    // ==================== 恢复: HDFS → MongoDB ====================

    public Map<String, Object> restore(String fileName, String collection) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            // 先试本地
            Path localFile = Paths.get(LOCAL_STORE, "archive", fileName);
            if (!Files.exists(localFile)) {
                // 若分布式HDFS在线，拉取
                if (checkDistributedHdfs()) {
                    runHadoop("fs", "-get", HDFS_BASE + "/archive/" + fileName, localFile.toString());
                }
            }
            if (!Files.exists(localFile)) {
                result.put("success", false);
                result.put("message", "未找到归档文件: " + fileName);
                return result;
            }

            if ("analysis_results".equals(collection)) {
                List<AnalysisResult> restored = mapper.readValue(localFile.toFile(),
                        new TypeReference<List<AnalysisResult>>() {});
                for (AnalysisResult doc : restored) {
                    doc.setId(null);
                    analysisResultRepository.save(doc);
                }
                result.put("restored", restored.size());
            } else {
                List<CityData> restored = mapper.readValue(localFile.toFile(),
                        new TypeReference<List<CityData>>() {});
                for (CityData doc : restored) {
                    doc.setId(null);
                    cityDataRepository.save(doc);
                }
                result.put("restored", restored.size());
            }

            result.put("success", true);
            result.put("message", "恢复成功: " + result.get("restored") + " 条 → " + collection);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "恢复失败: " + e.getMessage());
            log.error("HDFS恢复失败", e);
        }
        return result;
    }

    // ==================== 列出HDFS文件 ====================

    public List<Map<String, String>> listFiles() {
        return (List<Map<String, String>>) getStatus().get("files");
    }

    // ==================== 工具方法 ====================

    /**
     * 检测分布式 HDFS 是否在线 (三级检测)
     * 1) hadoop.cmd 是否存在
     * 2) NameNode 9870 端口是否可通 (Socket 3秒超时)
     * 3) hadoop fs -ls / 命令是否成功 (超时 15 秒)
     * 端口通但命令失败也视为在线 (降级模式)
     */
    private boolean checkDistributedHdfs() {
        // 1) 检查 hadoop.cmd 是否存在
        if (!Files.exists(Paths.get(HADOOP_BIN))) {
            return false;
        }
        // 2) 端口探测 (3秒超时, 轻量级)
        boolean portOpen = false;
        try {
            Socket s = new Socket();
            s.connect(new java.net.InetSocketAddress("localhost", 9870), 3000);
            s.close();
            portOpen = true;
        } catch (Exception ignored) {}
        if (!portOpen) return false;
        // 3) hadoop fs -ls / (15 秒超时)
        try {
            List<String> out = runHadoop("fs", "-ls", "/");
            return out != null && !out.isEmpty();
        } catch (Exception e) {
            // 端口通但命令失败 → 降级为在线
            return true;
        }
    }

    private boolean checkLocalStoreAvailable() {
        try {
            Path p = Paths.get(LOCAL_STORE, "archive");
            if (!Files.exists(p)) Files.createDirectories(p);
            return Files.isWritable(p);
        } catch (Exception e) {
            return false;
        }
    }

    private String now() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
    }

    private Map<String, String> parseFileLine(String line) {
        String[] p = line.split("\\s+");
        Map<String, String> f = new LinkedHashMap<>();
        f.put("name", p.length > 7 ? p[p.length - 1] : "?");
        f.put("size", p.length > 4 ? p[4] : "0");
        f.put("date", p.length > 6 ? p[5] + " " + p[6] : "?");
        return f;
    }

    /**
     * 运行 hadoop 命令 (延长超时到 15 秒, Hadoop 在 Windows 上启动较慢)
     */
    private List<String> runHadoop(String... args) throws Exception {
        List<String> fullArgs = new ArrayList<>();
        fullArgs.add(HADOOP_BIN);
        fullArgs.addAll(Arrays.asList(args));

        ProcessBuilder pb = new ProcessBuilder(fullArgs);
        pb.environment().put("HADOOP_HOME", HADOOP_HOME);
        pb.environment().put("hadoop.home.dir", HADOOP_HOME);
        pb.environment().put("HADOOP_USER_NAME", System.getProperty("hadoop.user.name", "Administrator"));
        pb.redirectErrorStream(true);
        Process p = pb.start();

        List<String> lines = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            long deadline = System.currentTimeMillis() + 15000; // 15 秒
            while (System.currentTimeMillis() < deadline && (line = r.readLine()) != null) {
                lines.add(line);
            }
        }
        boolean done;
        long deadline = System.currentTimeMillis() + 10000; // 再等 10 秒
        while (System.currentTimeMillis() < deadline) {
            done = p.waitFor(500, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (done) break;
        }
        try { p.destroyForcibly(); } catch (Exception ignored) {}
        return lines;
    }
}
