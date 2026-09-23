package com.example.qwenandanimagine.backend.dto;

/** POST /api/chat 的请求体:{"message":"银发少女站在樱花树下"} */
public class ChatRequest {
    private String message;

    public ChatRequest() {}
    public ChatRequest(String message) { this.message = message; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
