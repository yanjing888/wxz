package com.wuxiaozhi.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ReviewFeedbackRequest {
    @NotBlank
    private String rating;
}
