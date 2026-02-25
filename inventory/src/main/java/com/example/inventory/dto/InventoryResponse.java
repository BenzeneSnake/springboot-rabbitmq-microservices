package com.example.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "庫存查詢回應")
public class InventoryResponse {

    @Schema(description = "商品 ID", example = "P001")
    private String productId;

    @Schema(description = "目前庫存數量", example = "100")
    private Integer quantity;

    @Schema(description = "最後更新時間", example = "2026-02-25T10:00:00")
    private LocalDateTime updatedAt;
}
