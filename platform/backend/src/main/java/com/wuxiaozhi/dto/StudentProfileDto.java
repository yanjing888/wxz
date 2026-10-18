package com.wuxiaozhi.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class StudentProfileDto {

    private int assignedCount;
    private int startedCount;
    private int finishedCount;
    private int dataSubmitCount;
    private int correctionCount;
    private int helpCount;
    private int tutViewCount;
    private long labMinutes;

    private List<CapabilityDto> capabilities = new ArrayList<>();
    private List<TimelineItemDto> timeline = new ArrayList<>();

    @Data
    public static class CapabilityDto {
        private String key;
        private String label;
        private int score;
        private String hint;

        public CapabilityDto(String key, String label, int score, String hint) {
            this.key = key;
            this.label = label;
            this.score = score;
            this.hint = hint;
        }
    }

    @Data
    public static class TimelineItemDto {
        private Long sessionId;
        private String experimentCode;
        private String experimentName;
        private String status;
        private String startTime;
        private String endTime;
        private int correctionCount;
        private int dataSubmitCount;
    }
}
