package com.example.order.mq;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置類
 *
 * 這裡定義了訊息傳遞的「路線」：
 *
 *   Producer ──→ Exchange ──→ Queue ──→ Consumer
 *   (Order)      (分信中心)    (信箱)    (Payment)
 */
@Configuration
public class RabbitConfig {

    // ========== 1. 定義名稱常數 ==========

    // Exchange 名稱（分信中心的名字）
    public static final String ORDER_EXCHANGE = "order.exchange";

    // Queue 名稱（信箱的名字）
    public static final String ORDER_QUEUE = "order.queue";

    // Routing Key（郵遞區號，用來決定訊息送到哪個 Queue）
    public static final String ORDER_CREATED_ROUTING_KEY = "order.created";

    // ========== 2. 建立 Exchange（分信中心）==========

    /**
     * Topic Exchange：可以用萬用字元匹配 routing key
     * 例如：order.* 可以匹配 order.created, order.cancelled 等
     */
    @Bean
    public TopicExchange orderExchange() {
        return new TopicExchange(ORDER_EXCHANGE);
    }

    // ========== 3. 建立 Queue（信箱）==========

    /**
     * 建立一個持久化的 Queue
     * durable = true 表示 RabbitMQ 重啟後，Queue 還會存在
     */
    @Bean
    public Queue orderQueue() {
        return new Queue(ORDER_QUEUE, true);  // true = durable（持久化）
    }

    // ========== 4. 綁定 Exchange 和 Queue ==========

    /**
     * 告訴 RabbitMQ：
     * 當訊息送到 order.exchange，且 routing key 是 "order.created" 時，
     * 請把訊息轉發到 order.queue
     */
    @Bean
    public Binding orderBinding(Queue orderQueue, TopicExchange orderExchange) {
        return BindingBuilder
                .bind(orderQueue)           // 綁定這個 Queue
                .to(orderExchange)          // 到這個 Exchange
                .with(ORDER_CREATED_ROUTING_KEY);  // 當 routing key 匹配時
    }

    // ========== 5. 設定訊息格式為 JSON ==========

    /**
     * 使用 JSON 格式來序列化訊息
     * 這樣訊息內容會是人類可讀的 JSON，而不是 Java 序列化的二進位
     */
    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * 配置 RabbitTemplate（用來發送訊息的工具）
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }
}
