package com.wuxiaozhi.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AppPropertiesTest {

    @Test
    void exposesBenchCameraConfiguration() {
        assertThat(AppProperties.class)
                .hasDeclaredFields("benchCamera");
    }
}
