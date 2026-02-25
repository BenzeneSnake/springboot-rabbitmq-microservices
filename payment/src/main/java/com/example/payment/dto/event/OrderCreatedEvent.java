package com.example.payment.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 訂單建立事件（從 Order Service 接收）
 * 結構必須與 Order Service 發送的一致
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreatedEvent {

    private String eventId;           // 事件 ID（用於冪等性檢查）
    private String orderId;           // 訂單 ID
    private String customerId;        // 客戶 ID
    private BigDecimal totalAmount;   // 訂單總金額
    private List<OrderItemEvent> items;  // 訂單項目
    private LocalDateTime createdAt;  // 訂單建立時間
    private LocalDateTime eventTime;  // 事件發送時間
}
