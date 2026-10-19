package com.wuxiaozhi.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/images/**").addResourceLocations("classpath:/static/app/images/");
        registry.addResourceHandler("/newton-rings-prelab/**").addResourceLocations("classpath:/static/app/newton-rings-prelab/");
        registry.addResourceHandler("/newton-rings-simulation/**").addResourceLocations("classpath:/static/app/newton-rings-simulation/");
    }
}
