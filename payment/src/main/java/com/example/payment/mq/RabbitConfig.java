package com.example.payment.mq;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置類
 * <p>
 * 這裡定義了訊息傳遞的「路線」：
 * <p>
 *   Producer ──→ Exchange ──→ Queue ──→ Consumer
 *   (Order)      (分信中心)    (信箱)    (Payment)
 * <p>
 *   Payment Service 角色：
 *   1. Consumer: 監聽 order.queue，接收 OrderCreatedEvent
 *   2. Producer: 發送 PaymentCompletedEvent 到 payment.exchange
 */
@Configuration
public class RabbitConfig {

    // ========== Consumer 設定（監聽 Order Service 的訊息）==========
    public static final String ORDER_EXCHANGE = "order.exchange";
    public static final String ORDER_QUEUE = "order.queue";
    public static final String ORDER_ROUTING_KEY = "order.created";

    // ========== Producer 設定（發送給 Inventory Service）==========
    // ========== 1. 定義名稱常數 ==========
    // Exchange 名稱（分信中心的名字）
    public static final String PAYMENT_EXCHANGE = "payment.exchange";
    // Routing Key（郵遞區號，用來決定訊息送到哪個 Queue）
    public static final String PAYMENT_ROUTING_KEY = "payment.completed";
    
    // Inventory Queue（由 Payment Service 建立，給 Inventory Service 消費）
    public static final String INVENTORY_QUEUE = "inventory.queue";
    public static final String INVENTORY_DLX_EXCHANGE = "inventory.dlx.exchange";
    public static final String INVENTORY_DLQ = "inventory.dlq";
    public static final String INVENTORY_DLX_ROUTING_KEY = "inventory.failed";

    /**
     * Payment Exchange（Topic 類型）
     * <p>
     * TopicExchange 是在幹嘛？
     * 依 routing key + pattern 比對，決定訊息要進哪些 Queue
     */
    @Bean
    public TopicExchange paymentExchange() {
        return ExchangeBuilder
                .topicExchange(PAYMENT_EXCHANGE)
                .durable(true)
                .build();
    }

    /**
     * 如果不存在 → 自動建立
     * 如果存在 → 檢查一致後使用
     * @return
     */
    @Bean
    public Queue orderQueue() {
        return QueueBuilder
                .durable(ORDER_QUEUE)
                .build();
    }

    @Bean
    public TopicExchange orderExchange() {
        return ExchangeBuilder
                .topicExchange(ORDER_EXCHANGE)
                .durable(true)
                .build();
    }

    @Bean
    public Binding orderBinding() {
        return BindingBuilder
                .bind(orderQueue())
                .to(orderExchange())
                .with(ORDER_ROUTING_KEY);
    }

    /**
     * Inventory Queue（帶 DLX 設定）
     */
    @Bean
    public Queue inventoryQueue() {
        return QueueBuilder
                .durable(INVENTORY_QUEUE)
                .withArgument("x-dead-letter-exchange", INVENTORY_DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", INVENTORY_DLX_ROUTING_KEY)
                .build();
    }


    /**
     * Inventory Dead Letter Exchange
     */
    @Bean
    public TopicExchange inventoryDlxExchange() {
        return ExchangeBuilder
                .topicExchange(INVENTORY_DLX_EXCHANGE)
                .durable(true)
                .build();
    }

    /**
     * Inventory Dead Letter Queue
     */
    @Bean
    public Queue inventoryDlq() {
        return QueueBuilder
                .durable(INVENTORY_DLQ)
                .build();
    }

    /**
     * 綁定：payment.exchange → inventory.queue
     */
    @Bean
    public Binding inventoryQueueBinding() {
        return BindingBuilder
                .bind(inventoryQueue())
                .to(paymentExchange())
                .with(PAYMENT_ROUTING_KEY);
    }

    /**
     * 綁定：inventory.dlx.exchange → inventory.dlq
     */
    @Bean
    public Binding inventoryDlqBinding() {
        return BindingBuilder
                .bind(inventoryDlq())
                .to(inventoryDlxExchange())
                .with(INVENTORY_DLX_ROUTING_KEY);
    }

    // ========== 共用設定 ==========

    /**
     * JSON 訊息轉換器
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * RabbitTemplate 設定
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }

    /**
     * Listener Container Factory（手動 ACK）
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter());
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);  // 手動 ACK
        factory.setPrefetchCount(10);  // 每次最多取 10 個訊息
        return factory;
    }
}
