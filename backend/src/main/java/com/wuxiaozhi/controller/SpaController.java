package com.wuxiaozhi.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaController {

    @GetMapping({
            "/",
            "/login",
            "/lab",
            "/teacher",
            "/home",
            "/ai",
            "/ai/**",
            "/experiment/**",
            "/experiments/**",
            "/data/**",
            "/after/**",
            "/agents/**",
            "/prep/**",
            "/monitor",
            "/files",
            "/profile"
    })
    public String app() {
        return "forward:/app/index.html";
    }
}
