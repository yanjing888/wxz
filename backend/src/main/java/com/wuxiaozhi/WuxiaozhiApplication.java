package com.wuxiaozhi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.wuxiaozhi.config.AppProperties;
import com.wuxiaozhi.config.DifyProperties;

@SpringBootApplication
@EnableConfigurationProperties({ AppProperties.class, DifyProperties.class })
public class WuxiaozhiApplication {

    public static void main(String[] args) {
        SpringApplication.run(WuxiaozhiApplication.class, args);
    }
}
