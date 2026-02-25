package com.example.payment.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 訂單項目事件（從 Order Service 接收）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemEvent {

    private String productId;    // 商品 ID
    private Integer quantity;    // 數量
    private BigDecimal price;    // 單價
}
