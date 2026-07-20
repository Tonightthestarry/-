package com.massdata;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 城市级多源海量数据智能挖掘与可视化决策平台 - 启动类
 *
 * 技术栈: SpringBoot 3.2 + MongoDB + Redis + HBase + Hadoop + Spark + ECharts
 *
 * 八大模块:
 *  模块一: 海量数据分布式采集与存储 (Hadoop + NoSQL)
 *  模块二: 基于Spark的海量数据预处理与特征工程
 *  模块三: 海量数据深度挖掘 (Spark MLlib)
 *  模块四: 实时离线分析引擎 (Spark Streaming + MapReduce)
 *  模块五: JavaEE后端服务 (SpringBoot + MyBatis)
 *  模块六: 数据可视化大屏 (Vue + ECharts)
 *  模块七: 系统集成与全链路联调
 *  模块八: 项目成果输出
 *
 * 数据源(五类城市数据):
 *  1.交通数据 2.气象数据 3.舆情数据 4.消费数据 5.人口数据
 */
@SpringBootApplication
@EnableScheduling
@MapperScan("com.massdata.dao.mapper")
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
        System.out.println("================================================================");
        System.out.println("  城市级多源海量数据智能挖掘与可视化决策平台 v1.0");
        System.out.println("  启动成功! 访问: http://localhost:8088");
        System.out.println("  技术栈: SpringBoot3 + MongoDB + Redis + HBase + Spark + ECharts");
        System.out.println("  H2控制台: http://localhost:8088/h2-console");
        System.out.println("================================================================");
    }
}
