package com.example.order.mq;

import com.example.order.dto.event.OrderCreatedEvent;
import com.example.order.dto.event.OrderItemEvent;
import com.example.order.entity.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 訂單事件發布者
 *
 * 負責將訂單相關事件發送到 RabbitMQ
 *
 * 使用方式：
 *   Order savedOrder = orderRepository.save(order);
 *   orderEventPublisher.publishOrderCreated(savedOrder);
 *
 * 發送流程：
 *   Order 實體 → 轉換成 OrderCreatedEvent → 發送到 order.exchange → routing key: order.created
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 發布訂單建立事件
     *
     * @param order 已儲存的訂單實體
     */
    public void publishOrderCreated(Order order) {
        // 1. 將 Order 實體轉換成 OrderCreatedEvent
        OrderCreatedEvent event = buildOrderCreatedEvent(order);

        // 2. 發送訊息到 RabbitMQ
        rabbitTemplate.convertAndSend(
                RabbitConfig.ORDER_EXCHANGE,           // Exchange 名稱
                RabbitConfig.ORDER_CREATED_ROUTING_KEY, // Routing Key
                event                                   // 訊息內容（會被轉換成 JSON）
        );

        log.info("Published OrderCreatedEvent: orderId={}, eventId={}",
                event.getOrderId(), event.getEventId());
    }

    /**
     * 將 Order 實體轉換成 OrderCreatedEvent
     *
     * 為什麼要轉換？
     * 1. 解耦：Consumer 不需要知道 Order 實體的結構
     * 2. 版本控制：Event 結構可以獨立演進
     * 3. 最小化資料：只傳送 Consumer 需要的欄位
     */
    private OrderCreatedEvent buildOrderCreatedEvent(Order order) {
        return OrderCreatedEvent.builder()
                .eventId(generateEventId())           // 產生唯一事件 ID（用於冪等性）
                .orderId(order.getId())
                .customerId(order.getCustomerId())
                .totalAmount(order.getTotalAmount())
                .items(order.getItems().stream()
                        .map(item -> OrderItemEvent.builder()
                                .productId(item.getProductId())
                                .quantity(item.getQuantity())
                                .price(item.getPrice())
                                .build())
                        .collect(Collectors.toList()))
                .createdAt(order.getCreatedAt())
                .eventTime(LocalDateTime.now())       // 事件發送時間
                .build();
    }

    /**
     * 產生唯一的事件 ID
     *
     * 這個 ID 用於：
     * 1. 冪等性檢查：Consumer 可以用這個 ID 判斷是否已處理過
     * 2. 追蹤：可以追蹤訊息從發送到消費的完整流程
     *
     * 格式：order-created-{UUID}
     */
    private String generateEventId() {
        return "order-created-" + UUID.randomUUID().toString();
    }
}
