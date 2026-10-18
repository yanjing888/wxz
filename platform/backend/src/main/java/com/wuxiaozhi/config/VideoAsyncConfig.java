package com.wuxiaozhi.config;

import org.springframework.context.annotation.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class VideoAsyncConfig implements WebMvcConfigurer {
    @Bean public ThreadPoolTaskExecutor videoExecutor(){var e=new ThreadPoolTaskExecutor();e.setCorePoolSize(4);e.setMaxPoolSize(32);e.setQueueCapacity(0);e.setThreadNamePrefix("classroom-video-");e.initialize();return e;}
    @Override public void configureAsyncSupport(AsyncSupportConfigurer config){config.setTaskExecutor(videoExecutor());config.setDefaultTimeout(0);}
}
