package com.example.order.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 訂單項目事件
 *
 * 用於 OrderCreatedEvent 中的訂單項目資訊
 *
 * 注意：不需要實作 Serializable
 * 原因：使用 Jackson2JsonMessageConverter 進行 JSON 序列化，而非 Java 原生二進制序列化
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemEvent {

    /**
     * 商品 ID
     */
    private String productId;

    /**
     * 數量
     */
    private Integer quantity;

    /**
     * 單價
     */
    private BigDecimal price;
}
