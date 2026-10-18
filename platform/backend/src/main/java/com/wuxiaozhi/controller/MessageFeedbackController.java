package com.wuxiaozhi.controller;

import com.wuxiaozhi.dto.SubmitMessageFeedbackRequest;
import com.wuxiaozhi.entity.MessageFeedback;
import com.wuxiaozhi.security.AuthSupport;
import com.wuxiaozhi.service.FeedbackService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sessions/{sessionId}/messages/{messageId}/feedback")
public class MessageFeedbackController {

    private final FeedbackService feedbackService;

    public MessageFeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping
    public MessageFeedback submit(@PathVariable Long sessionId,
                                  @PathVariable Long messageId,
                                  @Valid @RequestBody SubmitMessageFeedbackRequest req,
                                  Authentication authentication) {
        return feedbackService.submitFeedback(sessionId, messageId, AuthSupport.currentUserId(authentication), req);
    }
}
