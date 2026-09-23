package com.example.qwenandanimagine.backend.service;

import com.example.qwenandanimagine.backend.entity.ImageRecord;
import com.example.qwenandanimagine.backend.repo.ImageRecordRepository;
// 注意:Spring Boot 4 用的是 Jackson 3,包名是 tools.jackson.*
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class ImageService {

    private final ImageRecordRepository repo;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newHttpClient();

    @Value("${animagine.base-url}")
    private String baseUrl;
    @Value("${image.storage-dir}")
    private String storageDir;

    public ImageService(ImageRecordRepository repo, ObjectMapper mapper) {
        this.repo = repo; this.mapper = mapper;
    }

    public static final String DEFAULT_NEGATIVE =
        "lowres, bad anatomy, bad hands, text, error, missing fingers, extra digit, fewer digits, cropped, worst quality, low quality, jpeg artifacts, signature, watermark, blurry";

    /** 生成一张图并入库,返回可访问的 URL 路径 */
    public String generateAndSave(String userText, String prompt, String negative, int width, int height, long seed) throws Exception {
        // 1) 组装给 Python 的 JSON 请求体
        //    用 LinkedHashMap 而不是 Map.of:Map.of 不接受 null 值,这里显式可控
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("prompt", prompt);
        body.put("negative_prompt", negative);
        body.put("width", width);
        body.put("height", height);
        body.put("steps", 28);
        body.put("cfg", 7.0);
        body.put("seed", seed);
        String reqJson = mapper.writeValueAsString(body);

        // 2) 发 HTTP POST 给画图服务,拿回图片的 base64
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/generate"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(reqJson))
            .build();
        HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());

        // 画图服务不是 200 时,直接把它的返回内容抛出来,方便排查(否则后面会报难懂的 NPE)
        if (resp.statusCode() / 100 != 2) {
            throw new IllegalStateException("画图服务返回 " + resp.statusCode() + ":" + resp.body());
        }
        JsonNode json = mapper.readTree(resp.body());
        JsonNode b64Node = json.get("image_b64");
        if (b64Node == null || b64Node.isNull()) {
            throw new IllegalStateException("画图服务返回里没有 image_b64:" + resp.body());
        }
        byte[] imageBytes = Base64.getDecoder().decode(b64Node.asText());

        // 3) 存成文件(用随机名避免冲突)
        Path dir = Path.of(storageDir);
        Files.createDirectories(dir);
        String fileName = UUID.randomUUID().toString() + ".png";
        Files.write(dir.resolve(fileName), imageBytes);

        // 4) 落库
        ImageRecord rec = new ImageRecord();
        rec.setPrompt(prompt); rec.setNegativePrompt(negative);
        rec.setWidth(width); rec.setHeight(height); rec.setSeed(seed);
        rec.setImageUrl("/images/" + fileName); rec.setUserText(userText);
        repo.save(rec);

        return rec.getImageUrl();
    }
}
