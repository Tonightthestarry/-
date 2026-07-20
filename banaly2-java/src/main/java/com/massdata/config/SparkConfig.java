package com.massdata.config;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.streaming.Durations;
import org.apache.spark.streaming.api.java.JavaStreamingContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spark配置 - 可选启用(需要HADOOP_HOME/winutils.exe)
 * 设置 spark.enabled=true 才会初始化Spark
 */
@Configuration
@ConditionalOnProperty(name = "spark.enabled", havingValue = "true")
public class SparkConfig {

    @Value("${spark.app-name:CityDataMining}")
    private String appName;

    @Value("${spark.master:local[*]}")
    private String master;

    @Value("${spark.checkpoint-dir:./spark-checkpoint}")
    private String checkpointDir;

    @Bean
    public SparkConf sparkConf() {
        return new SparkConf()
                .setAppName(appName)
                .setMaster(master)
                .set("spark.sql.adaptive.enabled", "true")
                .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
                .set("spark.driver.memory", "2g")
                // 关闭Spark UI - 避免 javax.servlet 与 Spring Boot 3 的 jakarta.servlet 冲突
                .set("spark.ui.enabled", "false")
                .set("spark.ui.showConsoleProgress", "false");
    }

    @Bean
    public SparkSession sparkSession(SparkConf conf) {
        return SparkSession.builder()
                .config(conf)
                .getOrCreate();
    }

    @Bean
    public JavaSparkContext javaSparkContext(SparkSession sparkSession) {
        return JavaSparkContext.fromSparkContext(sparkSession.sparkContext());
    }

    @Bean
    public JavaStreamingContext javaStreamingContext(JavaSparkContext sc) {
        JavaStreamingContext ssc = new JavaStreamingContext(sc, Durations.seconds(5));
        ssc.checkpoint(checkpointDir);
        return ssc;
    }
}
