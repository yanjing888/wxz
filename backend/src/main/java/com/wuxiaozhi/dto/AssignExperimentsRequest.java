package com.wuxiaozhi.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AssignExperimentsRequest {
    private List<String> experimentCodes = new ArrayList<>();
}
