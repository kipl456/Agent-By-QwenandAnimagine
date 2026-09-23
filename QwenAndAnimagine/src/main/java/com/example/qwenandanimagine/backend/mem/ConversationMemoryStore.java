package com.example.qwenandanimagine.backend.mem;

import com.example.qwenandanimagine.backend.dto.ChatMessage;
// 注意:Spring Boot 4 用的是 Jackson 3,包名是 tools.jackson.*
// (旧教程里的 com.fasterxml.jackson.databind.ObjectMapper 在 Boot 4 里已经不在 classpath 上了)
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class ConversationMemoryStore {

    private static final String KEY = "conv:default";       // 单人版固定 key
    private static final int MAX_TURNS = 6;                 // 最多保留最近 6 轮(一问一答=2条)
    private static final Duration TTL = Duration.ofDays(7); // 7 天没聊自动过期

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public ConversationMemoryStore(StringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis; this.mapper = mapper;
    }

    /** 读回历史(Redis 里存的是 JSON 字符串) */
    public List<ChatMessage> load() {
        String raw = redis.opsForValue().get(KEY);
        if (raw == null) return new ArrayList<>();
        try {
            return mapper.readValue(raw, new TypeReference<List<ChatMessage>>() {});
        } catch (Exception e) {
            // 存坏了就当没有历史,不要让整个请求挂掉
            return new ArrayList<>();
        }
    }

    /** 写入:把新的一轮(用户问/助手答)加进去,超出窗口就丢最旧的 */
    public void append(String userContent, String assistantContent) {
        List<ChatMessage> list = load();
        list.add(new ChatMessage("user", userContent));
        list.add(new ChatMessage("assistant", assistantContent));
        // 只保留最近 MAX_TURNS*2 条(每轮2条)
        if (list.size() > MAX_TURNS * 2) {
            list = list.subList(list.size() - MAX_TURNS * 2, list.size());
        }
        try {
            redis.opsForValue().set(KEY, mapper.writeValueAsString(list), TTL);
        } catch (Exception e) {
            throw new RuntimeException("写入记忆失败", e);
        }
    }

    /** 清空记忆(可加个重置接口用) */
    public void clear() { redis.delete(KEY); }
}
