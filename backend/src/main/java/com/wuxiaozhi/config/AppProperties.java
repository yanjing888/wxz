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
    private BenchCamera benchCamera = new BenchCamera();
    private UvcCamera uvcCamera = new UvcCamera();

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

    @Data
    public static class BenchCamera {
        private boolean enabled = false;
        private String label = "Bench camera";
        private String protocol = "rtsp";
        private String rtspUrl = "";
        private String browserStreamUrl = "";
    }

    @Data
    public static class UvcCamera {
        private boolean enabled = true;
        private String label = "UVC Camera";
        private String pythonCommand = "python";
        private String scriptPath = "scripts/uvc_capture_server.py";
        private String host = "127.0.0.1";
        private int port = 8765;
        private int index = 1;
        private String backend = "dshow";
        private int width = 1920;
        private int height = 1080;
        private int fps = 30;
        private long startupTimeoutMs = 5000L;
        private long captureTimeoutMs = 8000L;
    }
}
