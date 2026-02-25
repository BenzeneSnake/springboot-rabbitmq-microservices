package com.example.payment.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 冪等性記錄
 * 用於追蹤已處理過的事件，防止重複消費
 *
 * 流程：
 * 1. 收到訊息 → 查 eventId 是否存在
 * 2. 不存在 → INSERT (status=PROCESSING) → 處理業務 → UPDATE (status=PROCESSED)
 * 3. 已存在且 PROCESSED → 跳過，直接 ACK
 * 4. 已存在且 PROCESSING → 可能前次處理中斷，需判斷是否重試
 */
@Entity
@Table(name = "idempotency_record",
        uniqueConstraints = @UniqueConstraint(columnNames = "event_id"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true)
    private String eventId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IdempotencyRecordStatus status;  // 事件的消費狀態

    @Column(name = "processed_at")
    private LocalDateTime processedAt;
}
