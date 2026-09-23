package com.example.qwenandanimagine.backend.dto;

/**
 * POST /api/chat 的返回体。
 * 字段名必须保持 prompt / imageUrl —— Part 8 的前端就是按这两个名字取值的。
 */
public class ChatResponse {
    private String prompt;      // Qwen 生成的英文 tags
    private String imageUrl;    // 图片访问地址,如 /images/xxx.png

    public ChatResponse() {}
    public ChatResponse(String prompt, String imageUrl) {
        this.prompt = prompt;
        this.imageUrl = imageUrl;
    }

    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
}
