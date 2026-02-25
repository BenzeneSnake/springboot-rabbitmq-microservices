package com.example.payment.repository;

import com.example.payment.entity.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IdempotencyRepository extends JpaRepository<IdempotencyRecord, Long> {

    /**
     * 依事件 ID 查詢冪等性記錄
     */
    Optional<IdempotencyRecord> findByEventId(String eventId);

    /**
     * 檢查事件是否已存在
     */
    boolean existsByEventId(String eventId);
}
