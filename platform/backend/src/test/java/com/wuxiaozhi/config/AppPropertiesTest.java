package com.wuxiaozhi.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.ByteArrayResource;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class AppPropertiesTest {

    @Test
    void exposesBenchCameraConfiguration() {
        assertThat(AppProperties.class)
                .hasDeclaredFields("benchCamera");
    }

    @Test
    void bindsCapturePathWithDevicePlaceholderFromApplicationYaml() throws Exception {
        var environment = new StandardEnvironment();
        for (var source : new YamlPropertySourceLoader().load("application", new FileSystemResource("../config/application.yml.example"))) {
            environment.getPropertySources().addLast(source);
        }
        var acquisition = Binder.get(environment).bind("wuxiaozhi.acquisition", AppProperties.Acquisition.class).get();
        assertThat(acquisition.getDevices().get("microscope").getCapturePath()).isEqualTo("/api/v1/devices/{deviceId}/captures");
    }

    @Test
    void bindsIndependentDevicesAndExperimentRoutingFromYaml() throws Exception {
        String yaml = """
                wuxiaozhi:
                  acquisition:
                    default-device: microscope
                    experiments:
                      other_experiment: camera-two
                    devices:
                      microscope:
                        base-url: http://127.0.0.1:8765
                      camera-two:
                        base-url: http://192.168.1.102:9000
                        device-id: camera-02
                        capture-path: /custom/{deviceId}/image
                        api-token: second-token
                """;
        var environment = new StandardEnvironment();
        var resource = new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8));
        for (var source : new YamlPropertySourceLoader().load("deployment", resource)) {
            environment.getPropertySources().addFirst(source);
        }
        var acquisition = Binder.get(environment).bind("wuxiaozhi.acquisition", AppProperties.Acquisition.class).get();
        assertThat(acquisition.getExperiments()).containsEntry("other_experiment", "camera-two");
        assertThat(acquisition.getDevices()).containsKeys("microscope", "camera-two");
        assertThat(acquisition.getDevices().get("camera-two").getBaseUrl()).isEqualTo("http://192.168.1.102:9000");
        assertThat(acquisition.getDevices().get("camera-two").getCapturePath()).isEqualTo("/custom/{deviceId}/image");
    }
}
