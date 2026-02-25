package com.example.order.controller;

import com.example.order.dto.OrderRequest;
import com.example.order.dto.OrderResponse;
import com.example.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Order", description = "訂單管理 API")
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "建立訂單", description = "依 Customer ID、商品建立訂單")
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody OrderRequest request) {
        log.info("Received create order request for customer: {}", request.getCustomerId());
        OrderResponse response = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "取得訂單", description = "依訂單編號取得訂單資料")
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(
            @Parameter(description = "訂單 ID", example = "ORD202602250001")
            @PathVariable String orderId) {
        log.info("Received get order request: {}", orderId);
        OrderResponse response = orderService.getOrder(orderId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "由CustomerId取得訂單", description = "依CustomerId取得訂單資料")
    @GetMapping
    public ResponseEntity<List<OrderResponse>> getOrdersByCustomerId(
            @RequestParam String customerId) {
        log.info("Received get orders request for customer: {}", customerId);
        List<OrderResponse> responses = orderService.getOrdersByCustomerId(customerId);
        return ResponseEntity.ok(responses);
    }

}
