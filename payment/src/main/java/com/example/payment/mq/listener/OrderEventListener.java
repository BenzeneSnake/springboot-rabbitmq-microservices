package com.example.payment.mq.listener;

import com.example.payment.application.command.ProcessPaymentCommand;
import com.example.payment.application.service.PaymentService;
import com.example.payment.dto.event.OrderCreatedEvent;
import com.example.payment.mq.RabbitConfig;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 監聽 Order Service 發送的訂單事件
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventListener {

    private final PaymentService paymentService;

    /**
     * 監聽 order.queue，處理 OrderCreatedEvent
     * 
     * 使用手動 ACK 模式：
     * - 處理成功：basicAck
     * - 處理失敗（可重試）：basicNack + requeue
     * - 處理失敗（不可重試）：basicNack → 進入 DLQ
     */
    @RabbitListener( queues = RabbitConfig.ORDER_QUEUE,
            containerFactory = "rabbitListenerContainerFactory")
    public void handleOrderCreated(OrderCreatedEvent event,
                                   Channel channel,
                                   @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        log.info("Received OrderCreatedEvent: orderId={}, eventId={}", 
                event.getOrderId(), event.getEventId());

        try {
            // 處理付款（包含 eventId 用於冪等性檢查，items 用於後續傳遞給 Inventory Service）
            ProcessPaymentCommand processPaymentCommand = ProcessPaymentCommand.builder()
                    .eventId(event.getEventId())
                    .orderId(event.getOrderId())
                    .customerId( event.getCustomerId())
                    .totalAmount(event.getTotalAmount())
                    .items(event.getItems())
                    .build();

            paymentService.processPayment(processPaymentCommand);

            // 處理成功，確認訊息
            // deliveryTag: 訊息標識，multiple: true = 確認此標識之前的所有訊息，false = 只確認這條
            channel.basicAck(deliveryTag, false);// deliveryTag,multiple
            log.info("Payment processed successfully, message acknowledged: orderId={}", 
                    event.getOrderId());

        } catch (Exception e) {
            log.error("Failed to process payment for order: {}", event.getOrderId(), e);
            handleFailure(channel, deliveryTag, e);
        }
    }

    /**
     * 處理失敗情況
     */
    private void handleFailure(Channel channel, long deliveryTag, Exception e) {
        try {
            // 目前簡單處理：失敗就進入 DLQ（不重新入隊）
            // 後續可加入重試次數判斷
            //equeue: true = 重新放回 queue，false = 丟棄或進入死信隊列
            channel.basicNack(deliveryTag, false, false);//deliveryTag, multiple, requeue
            log.warn("Message rejected and sent to DLQ");
        } catch (IOException ioException) {
            log.error("Failed to nack message", ioException);
        }
    }
}
