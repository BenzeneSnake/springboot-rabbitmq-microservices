package com.example.payment.entity;

/**
 * 事件的消費狀態
 */
public enum IdempotencyRecordStatus {
    PROCESSING,
    PROCESSED,
    FAILED
}
