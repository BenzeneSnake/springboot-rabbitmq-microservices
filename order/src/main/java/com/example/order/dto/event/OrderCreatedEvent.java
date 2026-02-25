package com.example.order.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 訂單建立事件
 *
 * 當訂單建立成功後，會發送這個事件到 RabbitMQ
 * Payment Service 會監聯這個事件來進行付款處理
 *
 * 結構化資料 序列化（變成二進制）的方法:
 * | 情境                     | 建議                             |
 * | ---------------------- | ------------------------------ |
 * | Spring + Rabbit + 快速開發 | Jackson2JsonMessageConverter |
 * | 想完全掌控 byte             | 手動 ObjectMapper                |
 * | Event 長期演進             | Avro / Protobuf                |
 * | 跨語言 / 大型系統           | Protobuf、Hessian                 |
 * | 老 Java-only 系統         | Serializable（Java原生序列化 不推薦新專案）        |
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreatedEvent {

    /**
     * 事件 ID（用於冪等性檢查）
     * 每個事件都有唯一的 ID，Consumer 可以用這個 ID 來判斷是否已經處理過
     */
    private String eventId;

    /**
     * 訂單 ID
     */
    private String orderId;

    /**
     * 客戶 ID
     */
    private String customerId;

    /**
     * 訂單總金額
     */
    private BigDecimal totalAmount;

    /**
     * 訂單項目列表
     */
    private List<OrderItemEvent> items;

    /**
     * 訂單建立時間
     */
    private LocalDateTime createdAt;

    /**
     * 事件發送時間
     */
    private LocalDateTime eventTime;
}
