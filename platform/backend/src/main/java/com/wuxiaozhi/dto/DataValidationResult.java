package com.wuxiaozhi.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DataValidationResult {
    private boolean ok = true;
    private List<String> errors = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
}
