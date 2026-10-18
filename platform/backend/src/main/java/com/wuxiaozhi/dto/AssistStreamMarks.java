package com.wuxiaozhi.dto;

import com.wuxiaozhi.dto.experiment.MarkDto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssistStreamMarks {
    private List<MarkDto> marks;
}
