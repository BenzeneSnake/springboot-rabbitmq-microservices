package com.example.payment.repository;

import com.example.payment.entity.Payment;
import com.example.payment.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, String> {

    /**
     * 依訂單 ID 查詢付款紀錄
     */
    Optional<Payment> findByOrderId(String orderId);

    /**
     * 依客戶 ID 查詢所有付款紀錄
     */
    List<Payment> findByCustomerId(String customerId);

    /**
     * 依狀態查詢付款紀錄
     */
    List<Payment> findByStatus(PaymentStatus status);

    /**
     * 檢查訂單是否已有付款紀錄
     */
    boolean existsByOrderId(String orderId);
}
