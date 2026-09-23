package com.example.qwenandanimagine.backend.controller;

import com.example.qwenandanimagine.backend.service.QwenService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Part 5.3 的临时验证接口:只用来确认 Java → Qwen 通了,跑通后可以删掉。 */
@RestController
@RequestMapping("/test")
public class TestController {
    private final QwenService qwenService;
    public TestController(QwenService qwenService) { this.qwenService = qwenService; }

    @PostMapping("/qwen")
    public Map<String, Object> qwen(@RequestBody Map<String, String> body) {
        String prompt = qwenService.askForPrompt(body.get("message"), List.of());
        return Map.of("prompt", prompt);
    }
}
