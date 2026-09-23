package com.example.qwenandanimagine.backend.repo;

import com.example.qwenandanimagine.backend.entity.ImageRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ImageRecordRepository extends JpaRepository<ImageRecord, Long> {
    // 按时间倒序取最近 N 张,供"历史画廊"
    List<ImageRecord> findTop50ByOrderByCreatedAtDesc();
}
