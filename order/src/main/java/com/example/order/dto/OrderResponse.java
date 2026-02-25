package com.example.order.dto;

import com.example.order.entity.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "訂單查詢回應")
public class OrderResponse {

    @Schema(description = "訂單ID", example = "001")
    private String orderId;
    @Schema(description = "客戶 ID", example = "P001")
    private String customerId;
    @Schema(description = "總金額", example = "1000")
    private BigDecimal totalAmount;
    @Schema(description = "訂單狀態", example = "1000")
    private OrderStatus status;
    @Schema(description = "訂單商品查詢回應")
    private List<OrderItemResponse> items;
    @Schema(description = "建立時間", example = "2007-12-03T10:15:30")
    private LocalDateTime createdAt;
    @Schema(description = "訊息")
    private String message;
}
