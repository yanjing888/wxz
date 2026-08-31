package com.wuxiaozhi.controller;

import com.wuxiaozhi.dto.MechanicalScaleRecognizeRequest;
import com.wuxiaozhi.dto.MechanicalScaleRecognizeResponse;
import com.wuxiaozhi.service.MechanicalScaleReadingService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/scale-reading")
public class MechanicalScaleController {

    private final MechanicalScaleReadingService readingService;

    public MechanicalScaleController(MechanicalScaleReadingService readingService) {
        this.readingService = readingService;
    }

    @PostMapping("/recognize")
    public MechanicalScaleRecognizeResponse recognize(@RequestBody MechanicalScaleRecognizeRequest request) {
        return readingService.recognize(request);
    }
}
