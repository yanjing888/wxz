package com.wuxiaozhi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuxiaozhi.dto.experiment.ExperimentConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExperimentConfigServiceTest {

    @Test
    void loadsExperimentManifestAndHydratesStepTutorialsFromMarkdown() throws Exception {
        ExperimentConfigService service = new ExperimentConfigService(new ObjectMapper());

        service.loadAll();

        ExperimentConfig config = service.getByCode("newton_rings");
        assertThat(config.getSteps()).containsKey("1");
        assertThat(config.getSteps().get("1").getTut()).isNotNull();
        assertThat(config.getSteps().get("1").getTut().getSteps()).isNotEmpty();
        assertThat(config.getSteps().get("1").getTut().getWarnings()).isNotEmpty();
        assertThat(config.getReportKnowledge()).isNotEmpty();
        assertThat(config.getReportPath()).isNotEmpty();
    }
}
