package com.example.inventory.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCompletedEvent {

    private String eventId;       // 用於冪等性檢查（Phase 3）
    private String orderId;
    private String customerId;
    private BigDecimal amount;
    private List<OrderItemEvent> items;
    private LocalDateTime completedAt;
}
