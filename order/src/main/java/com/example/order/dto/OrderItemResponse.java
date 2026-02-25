package com.example.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "訂單商品查詢回應")
public class OrderItemResponse {

    @Schema(description = "訂單ID", example = "001")
    private String id;
    @Schema(description = "商品 ID", example = "P001")
    private String productId;
    @Schema(description = "商品數量", example = "1")
    private Integer quantity;
    @Schema(description = "商品價格", example = "100")
    private BigDecimal price;
    @Schema(description = "總金額", example = "1000")
    private BigDecimal subtotal;
}
