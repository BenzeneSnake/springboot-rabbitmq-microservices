package com.example.payment.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 付款完成事件（發送給 Inventory Service）
 *
 * Inventory Service 收到後會進行庫存扣減
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCompletedEvent {

    private String eventId;           // 事件 ID（用於冪等性檢查）
    private String orderId;           // 訂單 ID
    private String customerId;        // 客戶 ID
    private String paymentId;         // 付款 ID
    private String transactionId;     // 交易 ID
    private BigDecimal amount;        // 付款金額
    private List<OrderItemEvent> items;  // 訂單項目（用於扣減庫存）
    private LocalDateTime eventTime;  // 事件發送時間
}
