package com.wuxiaozhi.controller;

import com.wuxiaozhi.dto.*;
import com.wuxiaozhi.security.AuthSupport;
import com.wuxiaozhi.service.AiToolCatalogService;
import com.wuxiaozhi.service.AiToolService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/ai")
public class AiToolController {

    private final AiToolCatalogService catalogService;
    private final AiToolService aiToolService;

    public AiToolController(AiToolCatalogService catalogService, AiToolService aiToolService) {
        this.catalogService = catalogService;
        this.aiToolService = aiToolService;
    }

    @GetMapping("/tools")
    public List<AiToolItemDto> tools(Authentication authentication) {
        return catalogService.listTools(authentication);
    }

    @GetMapping("/tools/{toolCode}/conversations")
    public List<AiConversationItemDto> conversations(@PathVariable String toolCode, Authentication authentication) {
        catalogService.requireTool(toolCode, authentication);
        return aiToolService.listConversations(AuthSupport.currentUserId(authentication), toolCode);
    }

    @PostMapping("/tools/{toolCode}/conversations")
    public AiConversationItemDto createConversation(@PathVariable String toolCode, Authentication authentication) {
        catalogService.requireTool(toolCode, authentication);
        return aiToolService.createConversation(AuthSupport.currentUserId(authentication), toolCode);
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public List<AiMessageItemDto> messages(@PathVariable Long conversationId, Authentication authentication) {
        return aiToolService.listMessages(AuthSupport.currentUserId(authentication), conversationId);
    }

    @GetMapping("/recap/sessions")
    public List<AiRecapSessionItemDto> recapSessions(Authentication authentication) {
        return aiToolService.listRecapSessions(AuthSupport.currentUserId(authentication));
    }

    @PostMapping("/tools/{toolCode}/invoke")
    public AiToolInvokeResponse invoke(@PathVariable String toolCode,
                                       @RequestBody AiToolInvokeRequest request,
                                       Authentication authentication) {
        catalogService.requireTool(toolCode, authentication);
        return aiToolService.invoke(AuthSupport.currentUserId(authentication), toolCode, request);
    }

    @PostMapping(value = "/conversations/{conversationId}/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@PathVariable Long conversationId,
                                 @Valid @RequestBody AiChatRequest request,
                                 Authentication authentication) {
        return aiToolService.chatStream(AuthSupport.currentUserId(authentication), conversationId, request, authentication);
    }
}
