package com.wuxiaozhi.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ImportStudentsResult {
    private int created;
    private int updated;
    private int skipped;
    private int assigned;
    private List<Long> userIds = new ArrayList<>();
    private List<String> errors = new ArrayList<>();
}
