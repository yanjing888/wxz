package com.wuxiaozhi.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SubmitMessageFeedbackRequest {
    @NotBlank
    private String rating;
}
