package com.wuxiaozhi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.wuxiaozhi.config.AppProperties;
import com.wuxiaozhi.config.DifyProperties;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

@SpringBootApplication
@org.springframework.scheduling.annotation.EnableScheduling
@EnableConfigurationProperties({ AppProperties.class, DifyProperties.class })
public class WuxiaozhiApplication {

    public static void main(String[] args) {
        loadExternalEnvFiles();
        SpringApplication application = new SpringApplication(WuxiaozhiApplication.class);
        Path config = java.util.stream.Stream.of(Path.of("config", "application.yml"), Path.of("..", "config", "application.yml"))
                .filter(Files::isRegularFile).findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing config/application.yml. Copy application.yml.example and configure MySQL before starting."));
        application.setDefaultProperties(Map.of("spring.config.additional-location",
                config.toAbsolutePath().normalize().toUri().toString()));
        application.run(args);
    }

    private static void loadExternalEnvFiles() {
        for (String name : new String[] { "dify.env" }) {
            for (Path path : configCandidates(name)) {
                if (Files.isRegularFile(path)) {
                    loadEnvFile(path);
                    break;
                }
            }
        }
    }

    private static Path[] configCandidates(String fileName) {
        return new Path[] {
                Path.of("config", fileName),
                Path.of("..", "config", fileName)
        };
    }

    private static void loadEnvFile(Path path) {
        try {
            Map<String, String> values = new LinkedHashMap<>();
            for (String raw : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int equals = line.indexOf('=');
                if (equals <= 0) {
                    continue;
                }
                String key = line.substring(0, equals).trim();
                String value = line.substring(equals + 1).trim();
                values.put(key, value);
            }
            values.forEach(WuxiaozhiApplication::setDefaultProperty);
        } catch (IOException ignored) {
            // Missing external env files should not prevent local development startup.
        }
    }

    private static void setDefaultProperty(String key, String value) {
        if (System.getProperty(key) == null && System.getenv(key) == null) {
            System.setProperty(key, value);
        }
    }

}
