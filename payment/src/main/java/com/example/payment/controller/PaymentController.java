package com.example.payment.controller;

import com.example.payment.application.service.PaymentService;
import com.example.payment.dto.PaymentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Payment", description = "付款查詢 API")
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(summary = "依付款 ID 查詢", description = "透過 paymentId 查詢單筆付款記錄")
    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResponse> getPayment(
            @Parameter(description = "付款 ID") @PathVariable String paymentId) {
        log.info("REST request to get payment: {}", paymentId);
        PaymentResponse response = paymentService.getPayment(paymentId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "依訂單 ID 查詢付款", description = "透過 orderId 查詢對應的付款記錄")
    @GetMapping("/order/{orderId}")
    public ResponseEntity<PaymentResponse> getPaymentByOrderId(
            @Parameter(description = "訂單 ID") @PathVariable String orderId) {
        log.info("REST request to get payment by orderId: {}", orderId);
        PaymentResponse response = paymentService.getPaymentByOrderId(orderId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "依客戶 ID 查詢所有付款紀錄", description = "透過 customerId 查詢該客戶的所有付款記錄")
    @GetMapping
    public ResponseEntity<List<PaymentResponse>> getPaymentsByCustomerId(
            @Parameter(description = "客戶 ID") @RequestParam String customerId) {
        log.info("REST request to get payments for customer: {}", customerId);
        List<PaymentResponse> responses = paymentService.getPaymentsByCustomerId(customerId);
        return ResponseEntity.ok(responses);
    }
}
