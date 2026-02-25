package com.example.payment.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, unique = true)
    private String orderId;              // 關聯的訂單 ID

    @Column(nullable = false)
    private String customerId;           // 客戶 ID

    @Column(nullable = false)
    private BigDecimal amount;           // 付款金額

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;        // 付款狀態

    private String transactionId;        // 第三方交易 ID（模擬）

    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}