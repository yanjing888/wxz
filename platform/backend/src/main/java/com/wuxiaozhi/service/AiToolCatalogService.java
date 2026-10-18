package com.wuxiaozhi.service;

import com.wuxiaozhi.config.AiToolDefinition;
import com.wuxiaozhi.config.AiToolsProperties;
import com.wuxiaozhi.config.DifyProperties;
import com.wuxiaozhi.dto.AiToolItemDto;
import com.wuxiaozhi.entity.UserRole;
import com.wuxiaozhi.security.AuthSupport;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class AiToolCatalogService {

    private final AiToolsProperties aiToolsProperties;
    private final DifyProperties difyProperties;

    public AiToolCatalogService(AiToolsProperties aiToolsProperties, DifyProperties difyProperties) {
        this.aiToolsProperties = aiToolsProperties;
        this.difyProperties = difyProperties;
    }

    public List<AiToolItemDto> listTools(Authentication authentication) {
        return aiToolsProperties.getItems().stream()
                .filter(tool -> isVisibleTo(authentication, tool))
                .map(this::toItem)
                .toList();
    }

    public AiToolDefinition requireTool(String code) {
        return aiToolsProperties.getItems().stream()
                .filter(tool -> tool.getCode().equals(code))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AI 功能不存在"));
    }

    public AiToolDefinition requireTool(String code, Authentication authentication) {
        AiToolDefinition tool = requireTool(code);
        if (!isVisibleTo(authentication, tool)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "该功能不对学生开放");
        }
        return tool;
    }

    private boolean isVisibleTo(Authentication authentication, AiToolDefinition tool) {
        if (tool.isStudentVisible()) {
            return true;
        }
        return authentication != null && AuthSupport.hasRole(authentication, UserRole.TEACHER);
    }

    public String resolveWorkflowKey(AiToolDefinition tool) {
        String key = tool.getWorkflowKey() != null && !tool.getWorkflowKey().isBlank()
                ? tool.getWorkflowKey().trim()
                : tool.getCode();
        if (difyProperties.canRun(key)) {
            return key;
        }
        if (difyProperties.canRun("text-assist")) {
            return "text-assist";
        }
        return key;
    }

    private AiToolItemDto toItem(AiToolDefinition tool) {
        String workflowKey = resolveWorkflowKey(tool);
        AiToolItemDto dto = new AiToolItemDto();
        dto.setCode(tool.getCode());
        dto.setName(tool.getName());
        dto.setDescription(tool.getDescription());
        dto.setPageType(tool.getPageType());
        dto.setGroup(tool.getGroup());
        dto.setSuggestions(tool.getSuggestions());
        dto.setWorkflowKey(workflowKey);
        dto.setAvailable(difyProperties.canRun(workflowKey));
        return dto;
    }
}
