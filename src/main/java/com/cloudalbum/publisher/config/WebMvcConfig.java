package com.cloudalbum.publisher.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${release.downloads-location:file:./releases/}")
    private String downloadsLocation;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    /**
     * SPA fallback：所有非 /api、非静态资源、非下载资源的路由均返回 index.html，
     * 由前端 Vue Router 接管渲染。
     */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/index.html");
        registry.addViewController("/login").setViewName("forward:/index.html");
        registry.addViewController("/register").setViewName("forward:/index.html");
        registry.addViewController("/client-downloads").setViewName("forward:/index.html");
        registry.addViewController("/albums").setViewName("forward:/index.html");
        registry.addViewController("/albums/**").setViewName("forward:/index.html");
        registry.addViewController("/media").setViewName("forward:/index.html");
        registry.addViewController("/devices").setViewName("forward:/index.html");
        registry.addViewController("/distributions").setViewName("forward:/index.html");
        registry.addViewController("/admin/**").setViewName("forward:/index.html");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 静态资源映射（Spring Boot 默认已包含 classpath:/static/，此处显式声明保险）
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/");
        registry.addResourceHandler("/downloads/**")
                .addResourceLocations(normalizeResourceLocation(downloadsLocation));
    }

    private String normalizeResourceLocation(String location) {
        String normalized = location == null || location.isBlank() ? "file:./releases/" : location.trim();
        return normalized.endsWith("/") ? normalized : normalized + "/";
    }
}
