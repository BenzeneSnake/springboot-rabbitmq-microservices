package com.example.payment.mq.publisher;

import com.example.payment.dto.event.OrderItemEvent;
import com.example.payment.dto.event.PaymentCompletedEvent;
import com.example.payment.entity.Payment;
import com.example.payment.mq.RabbitConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 付款事件發布者
 *
 * 負責將付款完成事件發送到 RabbitMQ
 *
 * 發送流程：
 *   Payment 實體 → 轉換成 PaymentCompletedEvent →
 *   發送到 payment.exchange → routing key: payment.completed
 *            ↓
 *         inventory.queue
 *            ↓
 *         Inventory Service（扣減庫存）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 發布付款完成事件
     *
     * @param payment 已完成的付款實體
     * @param items   訂單項目（從 OrderCreatedEvent 傳遞過來，給 Inventory 扣庫存用）
     */
    public void publishPaymentCompleted(Payment payment, List<OrderItemEvent> items) {
        // 1. 建立 PaymentCompletedEvent
        PaymentCompletedEvent event = buildPaymentCompletedEvent(payment, items);

        // 2. 發送訊息到 RabbitMQ
        rabbitTemplate.convertAndSend(
                RabbitConfig.PAYMENT_EXCHANGE,      // Exchange 名稱
                RabbitConfig.PAYMENT_ROUTING_KEY,   // Routing Key
                event                                // 訊息內容（會被轉換成 JSON）
        );

        log.info("Published PaymentCompletedEvent: orderId={}, paymentId={}, eventId={}",
                event.getOrderId(), event.getPaymentId(), event.getEventId());
    }

    /**
     * 將 Payment 實體轉換成 PaymentCompletedEvent
     */
    private PaymentCompletedEvent buildPaymentCompletedEvent(Payment payment, List<OrderItemEvent> items) {
        return PaymentCompletedEvent.builder()
                .eventId(generateEventId())
                .orderId(payment.getOrderId())
                .customerId(payment.getCustomerId())
                .paymentId(payment.getId())
                .transactionId(payment.getTransactionId())
                .amount(payment.getAmount())
                .items(items)
                .eventTime(LocalDateTime.now())
                .build();
    }

    /**
     * 產生唯一的事件 ID
     */
    private String generateEventId() {
        return "payment-completed-" + UUID.randomUUID().toString();
    }
}
