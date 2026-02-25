package com.example.inventory.mq;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    // Exchange names
    public static final String PAYMENT_EXCHANGE = "payment.exchange";
    public static final String INVENTORY_DLX_EXCHANGE = "inventory.dlx.exchange";

    // Queue names
    public static final String INVENTORY_QUEUE = "inventory.queue";
    public static final String INVENTORY_DLQ = "inventory.dlq";

    // Routing keys
    public static final String PAYMENT_COMPLETED_ROUTING_KEY = "payment.completed";
    public static final String INVENTORY_FAILED_ROUTING_KEY = "inventory.failed";

    // --- Exchanges ---

    @Bean
    TopicExchange paymentExchange() {
        return ExchangeBuilder.topicExchange(PAYMENT_EXCHANGE).durable(true).build();
    }

    @Bean
    TopicExchange inventoryDlxExchange() {
        return ExchangeBuilder.topicExchange(INVENTORY_DLX_EXCHANGE).durable(true).build();
    }

    // --- Queues ---

    @Bean
    Queue inventoryQueue() {
        return QueueBuilder.durable(INVENTORY_QUEUE)
                .withArgument("x-dead-letter-exchange", INVENTORY_DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", INVENTORY_FAILED_ROUTING_KEY)
                .build();
    }

    @Bean
    Queue inventoryDlq() {
        return QueueBuilder.durable(INVENTORY_DLQ).build();
    }

    // --- Bindings ---

    @Bean
    Binding inventoryQueueBinding() {
        return BindingBuilder.bind(inventoryQueue())
                .to(paymentExchange())
                .with(PAYMENT_COMPLETED_ROUTING_KEY);
    }

    @Bean
    Binding inventoryDlqBinding() {
        return BindingBuilder.bind(inventoryDlq())
                .to(inventoryDlxExchange())
                .with(INVENTORY_FAILED_ROUTING_KEY);
    }

    // --- Message Converter ---

    @Bean
    MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    // --- RabbitTemplate ---

    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }

    // --- Listener Container Factory ---
    // concurrency / prefetch 也可從 yml 讀取，這裡用 Bean 顯式設定方便理解

    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter());
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setPrefetchCount(10);
        factory.setConcurrentConsumers(2);
        factory.setMaxConcurrentConsumers(5);
        return factory;
    }
}
