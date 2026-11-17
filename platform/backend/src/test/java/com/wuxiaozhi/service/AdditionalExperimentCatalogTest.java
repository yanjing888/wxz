package com.wuxiaozhi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises the actual catalog loader and knowledge resolver without a teaching database. */
class AdditionalExperimentCatalogTest {
    @ParameterizedTest
    @ValueSource(strings = {"hall_effect", "millikan_oil_drop", "slide_wire_resistor",
            "thin_lens_focal_length", "equal_thickness_laser_wavelength", "spectrometer",
            "oscilloscope_usage", "sound_velocity", "photoelectric_effect"})
    void newExperimentsHaveUsableManualWorkflowsAndResolvableKnowledge(String code) throws Exception {
        var catalog = new ExperimentConfigService(new ObjectMapper());
        catalog.loadAll();
        var knowledge = new KnowledgeMapService();
        knowledge.load();
        var config = catalog.getByCode(code);

        assertThat(catalog.listAll()).extracting(item -> item.getCode()).contains(code);
        // This flag enables manual forms too; automatic acquisition is gated by dataSource.
        assertThat(config.getDataCollection().isEnabled()).isTrue();
        assertThat(config.getSimulation()).isNull();
        assertThat(config.getSteps()).hasSize(5);
        assertThat(config.getMenuLabels()).hasSize(5);
        assertThat(config.getReportKnowledge()).isNotEmpty();
        assertThat(config.getReportPath()).isNotEmpty();
        double total = 0;
        int dataSteps = 0;
        for (var entry : config.getSteps().entrySet()) {
            var step = entry.getValue();
            assertThat(step.getDataSource()).isEqualTo("manual");
            assertThat(step.getDeviceType()).isNull();
            assertThat(step.getDesc()).isNotBlank();
            assertThat(step.getTut().getSteps()).isNotEmpty();
            assertThat(step.getTut().getWarnings()).isNotEmpty();
            assertThat(step.getAssessmentCriteria()).isNotEmpty();
            assertThat(step.getAssessmentBlocking()).isFalse();
            total += step.getAssessmentMaxScore();
            var inputs = new HashMap<String, Object>();
            knowledge.enrichInputs(inputs, code, entry.getKey(), false);
            assertThat(inputs).containsEntry("teaching_doc", code + "/teaching-knowledge.md")
                    .containsEntry("rules_doc", code + "/visual-rules.md")
                    .containsEntry("teaching_section", code + ".step." + entry.getKey() + ".teaching");
            if ("data".equals(step.getCorrectionMode())) {
                dataSteps++;
                assertThat(step.getDataFields()).isNotEmpty();
                assertThat(new DataValidationService().validate(config, step, new HashMap<>()).isOk()).isFalse();
                assertThat(step.getDataFields()).allSatisfy(field -> {
                    assertThat(field.getKey()).isNotBlank();
                    assertThat(field.isPhotoAssist()).isFalse();
                });
            }
        }
        assertThat(total).isEqualTo(100);
        assertThat(dataSteps).isGreaterThanOrEqualTo(2);
    }
}
