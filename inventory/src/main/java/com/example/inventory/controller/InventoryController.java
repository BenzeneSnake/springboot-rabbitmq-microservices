package com.example.inventory.controller;

import com.example.inventory.dto.InventoryResponse;
import com.example.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Inventory", description = "庫存管理 API")
public class InventoryController {

    private final InventoryService inventoryService;

    @Operation(summary = "查詢單一商品庫存", description = "依 productId 查詢該商品的目前庫存數量")
    @GetMapping("/{productId}")
    public ResponseEntity<InventoryResponse> getInventory(
            @Parameter(description = "商品 ID", example = "P001") @PathVariable String productId) {
        log.info("Received get inventory request: productId={}", productId);
        InventoryResponse response = inventoryService.getInventory(productId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "查詢所有商品庫存", description = "取得所有商品的庫存列表")
    @GetMapping
    public ResponseEntity<List<InventoryResponse>> getAllInventory() {
        log.info("Received get all inventory request");
        List<InventoryResponse> responses = inventoryService.getAllInventory();
        return ResponseEntity.ok(responses);
    }
}
