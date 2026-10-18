package com.wuxiaozhi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
@ConfigurationProperties(prefix = "wuxiaozhi")
public class AppProperties {
    private Jwt jwt = new Jwt();
    private Upload upload = new Upload();
    private Experiments experiments = new Experiments();
    private BenchCamera benchCamera = new BenchCamera();
    private Acquisition acquisition = new Acquisition();

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
        /** Alternate FLV path for direct Ethernet link (/ws-direct/...). */
        private String browserStreamUrlDirect = "";
        /** auto | lab | direct */
        private String connectionMode = "auto";
    }

    @Data
    public static class Acquisition {
        private boolean enabled = true;
        private String defaultDevice = "microscope";
        private Map<String, String> experiments = new LinkedHashMap<>();
        private Map<String, AcquisitionDevice> devices = new LinkedHashMap<>(Map.of("microscope", new AcquisitionDevice()));
    }

    @Data
    public static class AcquisitionDevice {
        private boolean enabled = true;
        private String label = "牛顿环成像相机";
        private String baseUrl = "http://127.0.0.1:8765";
        private String capturePath = "/api/v1/devices/{deviceId}/captures";
        private String apiToken = "";
        private String deviceId = "microscope-01";
        private long connectTimeoutMs = 2000L;
        private long captureTimeoutMs = 25000L;
    }
}
