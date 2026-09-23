package com.example.qwenandanimagine.backend.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;

@RestController
public class ImageController {

    @Value("${image.storage-dir}")
    private String storageDir;

    @GetMapping("/images/{name}")
    public ResponseEntity<?> image(@PathVariable String name) {
        try {
            // 先把基准目录转成【绝对路径】再比较。
            // storageDir 是相对路径(./generated),而下面 resolve 出来的是相对路径,
            // 拿相对路径去 startsWith 绝对路径永远为 false,会导致每张图都返回 400。
            Path base = Path.of(storageDir).toAbsolutePath().normalize();
            // 只允许取 storageDir 里的文件,防止 ../../ 之类的路径穿越
            Path file = base.resolve(name).normalize();
            if (!file.startsWith(base)) {
                return ResponseEntity.badRequest().build();
            }
            UrlResource res = new UrlResource(file.toUri());
            if (res.exists()) {
                return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(res);
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}
