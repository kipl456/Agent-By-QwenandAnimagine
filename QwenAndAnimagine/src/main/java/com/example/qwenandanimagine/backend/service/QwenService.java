package com.example.qwenandanimagine.backend.service;

import com.example.qwenandanimagine.backend.dto.ChatMessage;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class QwenService {

    // 注入自动配置的 ChatClient.Builder,再 build() 出 ChatClient(它已经按 application.yml 连好 Ollama)
    private final ChatClient chatClient;

    public QwenService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    // 系统提示词:决定 Qwen"扮演什么角色、输出什么格式"。这是画质关键,可自行打磨
    private static final String SYSTEM_PROMPT = """
        你是一名动漫图片提示词工程师,为 Animagine XL 模型服务。
        用户会用中文描述想画的画面(可能引用之前提到的内容),如果你需要回复,那么你也要用中文回复.
        请把画面翻译成【只用一行英文、逗号分隔的 tags】:
        顺序尽量是:主体 → 细节/服装 → 场景/构图 → 光影。
        结尾必须加上质量词:masterpiece, best quality, absurdres。
        只输出这一行英文 tags,不要输出任何解释、引号、编号或负面提示词。
        """;

    /**
     * 根据 系统提示词 + 历史对话 + 用户新消息,向 Qwen 要一句英文提示词。
     * history 是最近几轮的 (用户, 助手) 交替消息。
     */
    public String askForPrompt(String userMessage, List<ChatMessage> history) {
        // 1) 把消息组装成一个"消息列表"——这就是"喂给模型的上下文"
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(SYSTEM_PROMPT));

        // 2) 把历史按 (用户→助手) 交替加进去,让 Qwen 记得前面聊过什么
        for (ChatMessage m : history) {
            if ("user".equals(m.getRole())) {
                messages.add(new UserMessage(m.getContent()));
            } else {
                messages.add(new AssistantMessage(m.getContent()));
            }
        }

        // 3) 加上用户当前这条
        messages.add(new UserMessage(userMessage));

        // 4) 调用模型(发一个请求到 Ollama),拿到回复文字
        return this.chatClient.prompt()
                .messages(messages)          // 整段塞给模型(含 SystemMessage + 历史 + 本轮)
                .call()
                .content();                  // 直接拿文本
    }
}
