package com.example.payment.entity;

public enum PaymentStatus {
    PENDING,      // 等待處理
    PROCESSING,   // 處理中
    COMPLETED,    // 完成
    FAILED        // 失敗
}