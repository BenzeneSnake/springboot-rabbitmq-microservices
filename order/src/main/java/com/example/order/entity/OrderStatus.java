package com.example.order.entity;

public enum OrderStatus {
    PENDING,        // 訂單已建立，等待處理
    PROCESSING,     // 處理中
    PAID,           // 已付款
    COMPLETED,      // 已完成
    CANCELLED,      // 已取消
    FAILED          // 失敗
}
