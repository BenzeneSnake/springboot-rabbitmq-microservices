package com.example.payment.application.command;

import com.example.payment.dto.event.OrderItemEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class ProcessPaymentCommand {
    private final String eventId;      // 冪等用
    private final String orderId;
    private final String customerId;
    private final BigDecimal totalAmount;
    private final List<OrderItemEvent> items;
}
