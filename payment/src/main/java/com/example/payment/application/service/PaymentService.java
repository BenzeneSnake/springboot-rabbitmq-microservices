package com.example.payment.application.service;

import com.example.payment.application.command.ProcessPaymentCommand;
import com.example.payment.dto.PaymentResponse;
import com.example.payment.dto.event.OrderItemEvent;
import com.example.payment.entity.IdempotencyRecord;
import com.example.payment.entity.IdempotencyRecordStatus;
import com.example.payment.entity.Payment;
import com.example.payment.entity.PaymentStatus;
import com.example.payment.exception.PaymentErrorCode;
import com.example.payment.exception.PaymentException;
import com.example.payment.mq.publisher.PaymentEventPublisher;
import com.example.payment.repository.IdempotencyRepository;
import com.example.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final IdempotencyRepository idempotencyRepository;
    private final PaymentEventPublisher paymentEventPublisher;

    /**
     * 處理付款（模擬）
     * 由 MQ Consumer (OrderEventListener) 呼叫此方法
     *
     * @param processPaymentCommand 付款命令物件
     * @return 付款結果
     */
    @Transactional
    public PaymentResponse processPayment(ProcessPaymentCommand processPaymentCommand) {
        log.info("Processing payment for order: {}, eventId: {}",
                processPaymentCommand.getOrderId(), processPaymentCommand.getEventId());

        // 1. 冪等性檢查：查詢是否已處理過此 eventId
        Optional<IdempotencyRecord> existing = idempotencyRepository.findByEventId(processPaymentCommand.getEventId());
        if (existing.isPresent()) {
            IdempotencyRecordStatus status = existing.get().getStatus();
            if (IdempotencyRecordStatus.PROCESSED == status) {
                log.info("Event {} already processed, skipping", processPaymentCommand.getEventId());
                Payment payment = paymentRepository.findByOrderId(processPaymentCommand.getOrderId()).orElseThrow();
                return toPaymentResponse(payment, "Event already processed (idempotent)");
            }
            if (IdempotencyRecordStatus.PROCESSING == status) {
                log.warn("Event {} is currently being processed, skipping", processPaymentCommand.getEventId());
                throw new RuntimeException("Event is currently being processed: " + processPaymentCommand.getEventId());
            }
        }

        // 2. 建立冪等性記錄（status=PROCESSING），防止並發重複處理
        IdempotencyRecord record = IdempotencyRecord.createIdempotencyRecord(processPaymentCommand.getEventId());
        idempotencyRepository.save(record);
        log.info("Idempotency record created: eventId={}, status=PROCESSING", processPaymentCommand.getEventId());

        // 3. 建立付款紀錄
        Payment payment = Payment.createPayment(processPaymentCommand.getOrderId(),
                processPaymentCommand.getCustomerId(),
                processPaymentCommand.getTotalAmount());

        payment = paymentRepository.save(payment);
        log.info("Payment created: paymentId={}, orderId={}", payment.getId(), processPaymentCommand.getOrderId());

        // 4. 模擬付款處理（實際環境會呼叫第三方 API）
        payment = simulatePaymentProcessing(payment);

        // 5. 付款成功，發送 PaymentCompletedEvent 給 Inventory Service
        paymentEventPublisher.publishPaymentCompleted(payment, processPaymentCommand.getItems());

        // 6. 更新冪等性記錄為 PROCESSED
        record.updateRecordStatus(record);
        idempotencyRepository.save(record);
        log.info("Idempotency record updated: eventId={}, status=PROCESSED", processPaymentCommand.getEventId());

        return toPaymentResponse(payment, "Payment processed successfully");
    }

    /**
     * 模擬付款處理
     */
    private Payment simulatePaymentProcessing(Payment payment) {
        // 模擬產生交易 ID
        String transactionId = "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        payment.setTransactionId(transactionId);
        payment.setStatus(PaymentStatus.COMPLETED);

        Payment savedPayment = paymentRepository.save(payment);
        log.info("Payment completed: paymentId={}, transactionId={}", payment.getId(), transactionId);

        return savedPayment;
    }

    /**
     * 依付款 ID 查詢
     */
    @Transactional(readOnly = true)
    public PaymentResponse getPayment(String paymentId) {
        log.info("Getting payment: {}", paymentId);

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> PaymentException.of(PaymentErrorCode.PAYMENTID_NOT_FOUND,paymentId));

        return toPaymentResponse(payment, null);
    }

    /**
     * 依訂單 ID 查詢付款
     */
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByOrderId(String orderId) {
        log.info("Getting payment for order: {}", orderId);

        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> PaymentException.of(PaymentErrorCode.PAYMENT_NOT_FOUND_FOR_ORDER,orderId));

        return toPaymentResponse(payment, null);
    }

    /**
     * 依客戶 ID 查詢所有付款紀錄
     */
    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentsByCustomerId(String customerId) {
        log.info("Getting payments for customer: {}", customerId);

        List<Payment> payments = paymentRepository.findByCustomerId(customerId);
        return payments.stream()
                .map(payment -> toPaymentResponse(payment, null))
                .toList();
    }

    /**
     * 轉換為 Response DTO
     */
    private PaymentResponse toPaymentResponse(Payment payment, String message) {
        return PaymentResponse.builder()
                .paymentId(payment.getId())
                .orderId(payment.getOrderId())
                .customerId(payment.getCustomerId())
                .amount(payment.getAmount())
                .status(payment.getStatus())
                .transactionId(payment.getTransactionId())
                .createdAt(payment.getCreatedAt())
                .completedAt(payment.getCompletedAt())
                .message(message)
                .build();
    }
}
