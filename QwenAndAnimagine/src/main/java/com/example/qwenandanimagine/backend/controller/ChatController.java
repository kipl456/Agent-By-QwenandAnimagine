package com.example.qwenandanimagine.backend.controller;

import com.example.qwenandanimagine.backend.dto.ChatMessage;
import com.example.qwenandanimagine.backend.dto.ChatRequest;
import com.example.qwenandanimagine.backend.dto.ChatResponse;
import com.example.qwenandanimagine.backend.entity.ImageRecord;
import com.example.qwenandanimagine.backend.mem.ConversationMemoryStore;
import com.example.qwenandanimagine.backend.repo.ImageRecordRepository;
import com.example.qwenandanimagine.backend.service.ImageService;
import com.example.qwenandanimagine.backend.service.QwenService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final QwenService qwen;
    private final ConversationMemoryStore memory;
    private final ImageService imageService;
    private final ImageRecordRepository imageRepo;

    public ChatController(QwenService qwen, ConversationMemoryStore memory,
                          ImageService imageService, ImageRecordRepository imageRepo) {
        this.qwen = qwen; this.memory = memory; this.imageService = imageService; this.imageRepo = imageRepo;
    }

    // 请求体:{"message":"银发少女站在樱花树下"}。单人版不需要会话ID。
    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest req) throws Exception {
        String userMessage = req.getMessage();

        // 1) 读历史(Redis)
        List<ChatMessage> history = memory.load();

        // 2) 让 Qwen 结合历史出英文提示词
        String prompt = qwen.askForPrompt(userMessage, history);

        // 3) 画一张图并入库,拿到 URL
        String imageUrl = imageService.generateAndSave(
            userMessage, prompt, ImageService.DEFAULT_NEGATIVE, 832, 1216, -1L);

        // 4) 把这轮写回记忆(Redis)。注意放在出图之后:
        //    出图失败时不写入,避免 Qwen 记住一个其实没画出来的画面
        memory.append(userMessage, prompt);

        // 5) 返回前端需要的所有东西
        return new ChatResponse(prompt, imageUrl);
    }

    // 历史画廊:返回最近 50 张图片记录
    @GetMapping("/history")
    public List<ImageRecord> history() {
        return imageRepo.findTop50ByOrderByCreatedAtDesc();
    }
}
