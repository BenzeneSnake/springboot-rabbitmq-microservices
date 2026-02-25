package com.example.order.service;

import com.example.order.dto.OrderItemRequest;
import com.example.order.dto.OrderItemResponse;
import com.example.order.dto.OrderRequest;
import com.example.order.dto.OrderResponse;
import com.example.order.entity.Order;
import com.example.order.entity.OrderItem;
import com.example.order.entity.OrderStatus;
import com.example.order.exception.OrderErrorCode;
import com.example.order.exception.OrderException;
import com.example.order.mq.OrderEventPublisher;
import com.example.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderEventPublisher orderEventPublisher;

    @Transactional
    public OrderResponse createOrder(OrderRequest request) {
        log.info("Creating order for customer: {}", request.getCustomerId());

        // 建立訂單
        Order order = Order.builder()
                .customerId(request.getCustomerId())
                .status(OrderStatus.PENDING)
                .totalAmount(BigDecimal.ZERO)
                .build();

        // 加入訂單項目並計算總金額
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (OrderItemRequest itemRequest : request.getItems()) {
            OrderItem item = OrderItem.builder()
                    .productId(itemRequest.getProductId())
                    .quantity(itemRequest.getQuantity())
                    .price(itemRequest.getPrice())
                    .build();
            order.addItem(item);
            totalAmount = totalAmount.add(item.getSubtotal());
        }
        order.setTotalAmount(totalAmount);

        // 儲存訂單
        Order savedOrder = orderRepository.save(order);
        log.info("Order created successfully: orderId={}", savedOrder.getId());

        // 發送訂單建立事件到 RabbitMQ
        orderEventPublisher.publishOrderCreated(savedOrder);

        return toOrderResponse(savedOrder, "Order created and processing...");
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(String orderId) {
        log.info("Getting order: {}", orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> OrderException.of(OrderErrorCode.ORDERID_NOT_FOUND, orderId));

        return toOrderResponse(order, null);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersByCustomerId(String customerId) {
        log.info("Getting orders for customer: {}", customerId);

        List<Order> orders = orderRepository.findByCustomerId(customerId);
        return orders.stream()
                .map(order -> toOrderResponse(order, null))
                .toList();
    }

    @Transactional
    public OrderResponse updateOrderStatus(String orderId, OrderStatus newStatus) {
        log.info("Updating order status: orderId={}, newStatus={}", orderId, newStatus);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> OrderException.of(OrderErrorCode.ORDERID_NOT_FOUND, orderId));

        order.setStatus(newStatus);
        Order updatedOrder = orderRepository.save(order);

        log.info("Order status updated: orderId={}, status={}", orderId, newStatus);
        return toOrderResponse(updatedOrder, "Order status updated");
    }

    private OrderResponse toOrderResponse(Order order, String message) {
        List<OrderItemResponse> itemResponses = order.getItems().stream()
                .map(this::toOrderItemResponse)
                .toList();

        return OrderResponse.builder()
                .orderId(order.getId())
                .customerId(order.getCustomerId())
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus())
                .items(itemResponses)
                .createdAt(order.getCreatedAt())
                .message(message)
                .build();
    }

    private OrderItemResponse toOrderItemResponse(OrderItem item) {
        return OrderItemResponse.builder()
                .id(item.getId())
                .productId(item.getProductId())
                .quantity(item.getQuantity())
                .price(item.getPrice())
                .subtotal(item.getSubtotal())
                .build();
    }
}
