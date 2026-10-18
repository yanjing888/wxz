package com.wuxiaozhi.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AppProperties appProperties;

    public WebMvcConfig(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/images/**").addResourceLocations("classpath:/static/app/images/");
        registry.addResourceHandler("/newton-rings-prelab/**").addResourceLocations("classpath:/static/app/newton-rings-prelab/");
        registry.addResourceHandler("/newton-rings-simulation/**").addResourceLocations("classpath:/static/app/newton-rings-simulation/");
        String uploadPath = Paths.get(appProperties.getUpload().getDir()).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(uploadPath.endsWith("/") ? uploadPath : uploadPath + "/");
    }
}
