package com.wuxiaozhi.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;

@Data
public class StepCompleteRequest {
    @Size(max = 2000) private String note;
    @Size(max = 50) private List<@Size(max = 20) List<@Size(max = 2000) String>> table;
}
