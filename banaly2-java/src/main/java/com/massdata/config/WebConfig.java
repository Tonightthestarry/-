package com.massdata.config;

import com.massdata.interceptor.JwtInterceptor;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web配置 - CORS跨域 + JWT拦截器 + .vue后缀支持
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;

    public WebConfig(JwtInterceptor jwtInterceptor) {
        this.jwtInterceptor = jwtInterceptor;
    }

    @Bean
    public WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> vueMimeCustomizer() {
        return factory -> {
            if (factory instanceof TomcatServletWebServerFactory tomcat) {
                tomcat.addContextCustomizers(context -> context.addMimeMapping("vue", "text/html"));
            }
        };
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/index.vue");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:5173", "http://127.0.0.1:5173",
                                "http://localhost:5174", "http://127.0.0.1:5174",
                                "http://localhost:8080", "http://127.0.0.1:8080",
                                "http://localhost:8081", "http://127.0.0.1:8081",
                                "http://localhost:8082", "http://127.0.0.1:8082",
                                "http://localhost:8088", "http://127.0.0.1:8088")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Authorization")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/login", "/api/auth/register",
                        "/api/streaming/recent", // 实时流查询(前端5秒轮询)
                        "/api/streaming/stats",  // 实时流各类型统计(KPI刷新)
                        "/api/monitor/streaming-status", // Spark Streaming状态(独立模块, 不影响主链路)
                        "/h2-console/**");
    }
}
