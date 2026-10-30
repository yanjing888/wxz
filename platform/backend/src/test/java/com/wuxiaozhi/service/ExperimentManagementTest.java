package com.wuxiaozhi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuxiaozhi.entity.ExperimentDefinition;
import com.wuxiaozhi.repository.ExperimentDefinitionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ExperimentManagementTest {
    @Test void savesTeachingContentAndRequirementsWithoutReplacingDeviceConfiguration() throws Exception {
        var mapper = new ObjectMapper();
        var service = new ExperimentConfigService(mapper); service.loadAll();
        var repository = mock(ExperimentDefinitionRepository.class);
        var db = new HashMap<String, ExperimentDefinition>();
        when(repository.findById(anyString())).thenAnswer(i -> Optional.ofNullable(db.get(i.getArgument(0))));
        when(repository.saveAndFlush(any())).thenAnswer(i -> { ExperimentDefinition e = i.getArgument(0); e.setVersion(e.getVersion() == null ? 0L : e.getVersion() + 1); db.put(e.getCode(), e); return e; });
        ReflectionTestUtils.setField(service, "definitions", repository);
        var config = service.getByCode("newton_rings");
        String device = mapper.writeValueAsString(config.getSteps().get("3").getDataFields());
        config.setDescription("实验目的与教学要求");
        config.setTeachingContent("## 实验目的\n教师修改后的实验内容");
        config.getSteps().get("1").setImageRequired(false);
        config.getSteps().get("1").setTableRequired(true);
        config.getSteps().get("1").getTut().setVideoUrl("/uploads/demo.mp4");
        config.getSteps().get("3").setDataFields(List.of()); // Not writable through this management endpoint.
        var result = service.saveTeachingConfig("newton_rings", config, -1L, 10L);
        assertThat(result.get("version")).isEqualTo(0L);
        var freshService = new ExperimentConfigService(mapper); freshService.loadAll(); ReflectionTestUtils.setField(freshService, "definitions", repository);
        var loaded = freshService.getByCode("newton_rings");
        assertThat(loaded.getDescription()).isEqualTo("实验目的与教学要求");
        assertThat(freshService.readTeachingKnowledge("newton_rings")).contains("教师修改后的实验内容");
        assertThat(loaded.getSteps().get("1").getImageRequired()).isFalse();
        assertThat(loaded.getSteps().get("1").getTableRequired()).isTrue();
        assertThat(loaded.getSteps().get("1").getTut().getVideoUrl()).isEqualTo("/uploads/demo.mp4");
        assertThat(mapper.writeValueAsString(loaded.getSteps().get("3").getDataFields())).isEqualTo(device);
        assertThatThrownBy(() -> service.saveTeachingConfig("newton_rings", config, -1L, 10L)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("其他教师");
        loaded.getSteps().get("1").getTut().setVideoUrl("javascript:alert(1)");
        assertThatThrownBy(() -> service.saveTeachingConfig("newton_rings", loaded, 0L, 10L)).hasMessageContaining("媒体地址");
        loaded.getSteps().remove("1");
        assertThatThrownBy(() -> service.saveTeachingConfig("newton_rings", loaded, 0L, 10L)).hasMessageContaining("步骤编号");
    }

    @Test void createsAnExperimentThatRemainsAvailableAfterReloadAndAllowsAppendingSteps() throws Exception {
        var mapper = new ObjectMapper(); var service = new ExperimentConfigService(mapper); service.loadAll();
        var repository = mock(ExperimentDefinitionRepository.class); var db = new HashMap<String, ExperimentDefinition>();
        when(repository.findById(anyString())).thenAnswer(i -> Optional.ofNullable(db.get(i.getArgument(0))));
        when(repository.findAll()).thenAnswer(i -> new ArrayList<>(db.values()));
        when(repository.saveAndFlush(any())).thenAnswer(i -> { ExperimentDefinition e = i.getArgument(0); e.setVersion(e.getVersion() == null ? 0L : e.getVersion() + 1); db.put(e.getCode(), e); return e; });
        ReflectionTestUtils.setField(service, "definitions", repository);
        var input = new com.wuxiaozhi.dto.experiment.ExperimentConfig(); input.setName("光学创新实验"); input.setCategory("optics"); input.setTeachingContent("实验目的：观察成像");
        var first = new com.wuxiaozhi.dto.experiment.StepConfig(); first.setTitle("搭建光路");
        input.setSteps(new LinkedHashMap<>(Map.of("1", first)));
        var created = service.createTeachingConfig(input, 10L);
        var config = (com.wuxiaozhi.dto.experiment.ExperimentConfig) created.get("config");
        assertThat(config.getCode()).startsWith("exp_");
        assertThat(service.listAll()).extracting(com.wuxiaozhi.dto.experiment.ExperimentConfig::getCode).contains(config.getCode());
        assertThat(config.getDataCollection().isEnabled()).isFalse();
        assertThat(config.getSimulation()).isNull();
        var second = new com.wuxiaozhi.dto.experiment.StepConfig(); second.setTitle("观察成像"); config.getSteps().put("2", second);
        service.saveTeachingConfig(config.getCode(), config, 0L, 10L);
        var fresh = new ExperimentConfigService(mapper); fresh.loadAll(); ReflectionTestUtils.setField(fresh, "definitions", repository);
        assertThat(fresh.getByCode(config.getCode()).getSteps()).hasSize(2);
        assertThat(fresh.readTeachingKnowledge(config.getCode())).isEqualTo("实验目的：观察成像");
    }
}
