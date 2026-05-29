package com.wuxiaozhi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "wuxiaozhi")
public class AppProperties {
    private Jwt jwt = new Jwt();
    private Upload upload = new Upload();
    private Experiments experiments = new Experiments();

    @Data
    public static class Jwt {
        private String secret;
        private long expirationMs = 86400000L;
    }

    @Data
    public static class Upload {
        private String dir = "./uploads";
    }

    @Data
    public static class Experiments {
        private String configDir = "classpath:experiments/";
    }
}
