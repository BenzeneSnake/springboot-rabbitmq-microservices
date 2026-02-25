package com.example.inventory.mq.listener;

import com.example.inventory.dto.event.PaymentCompletedEvent;
import com.example.inventory.exception.InventoryException;
import com.example.inventory.service.InventoryService;
import com.rabbitmq.client.Channel;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventListener {

    private static final int MAX_RETRY_COUNT = 3;

    private final InventoryService inventoryService;

    @RabbitListener(queues = "inventory.queue", concurrency = "2-5")
    public void handlePaymentCompleted(
            PaymentCompletedEvent event,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
            @Header(value = "x-death", required = false) List<Map<String, Object>> xDeath)
            throws IOException {

        log.info("Received PaymentCompletedEvent: orderId={}, eventId={}, thread={}",
                event.getOrderId(), event.getEventId(), Thread.currentThread().getName());

        try {
            // 超過重試次數 → 直接進 DLQ
            if (getRetryCount(xDeath) >= MAX_RETRY_COUNT) {
                log.warn("Max retry exceeded for orderId={}, routing to DLQ", event.getOrderId());
                channel.basicNack(deliveryTag, false, false);
                return;
            }

            inventoryService.processPaymentEvent(event);

            channel.basicAck(deliveryTag, false);
            log.info("ACK: orderId={}", event.getOrderId());

        } catch (InventoryException e) {
            // 業務錯誤（庫存不足、商品不存在）→ 不可重試，直接進 DLQ
            log.error("Business error for orderId={}, code={}, routing to DLQ",
                    event.getOrderId(), e.getInventoryErrorCode().getCode());
            channel.basicNack(deliveryTag, false, false);

        } catch (ObjectOptimisticLockingFailureException e) {
            // 並發衝突：另一 worker 已更新同一庫存列（@Version 不符）
            // → requeue 重試；超過 MAX_RETRY_COUNT 後由 x-death 機制送往 DLQ
            log.warn("Optimistic lock conflict for orderId={}, retryCount={}, will requeue: {}",
                    event.getOrderId(), getRetryCount(xDeath), e.getMessage());
            channel.basicNack(deliveryTag, false, true);

        } catch (Exception e) {
            // 其他暫時性錯誤（DB timeout 等）→ requeue 重試
            log.error("Transient error for orderId={}, will requeue: {}",
                    event.getOrderId(), e.getMessage());
            channel.basicNack(deliveryTag, false, true);
        }
    }

    /**
     * 從 x-death header 取得重試次數
     * RabbitMQ 每次訊息死信後會在 x-death 中記錄 count
     */
    private int getRetryCount(List<Map<String, Object>> xDeath) {
        if (xDeath == null || xDeath.isEmpty()) {
            return 0;
        }
        Object count = xDeath.get(0).get("count");
        if (count instanceof Long) {
            return ((Long) count).intValue();
        }
        return 0;
    }
}
