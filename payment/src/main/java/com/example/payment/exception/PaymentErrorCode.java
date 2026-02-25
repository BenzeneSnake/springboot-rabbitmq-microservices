package com.example.payment.exception;

import lombok.Getter;

@Getter
public enum PaymentErrorCode {
    SUCCESS("0000", "執行成功"),

    PAYMENTID_NOT_FOUND("1001", "無此paymentId"),
    PAYMENT_NOT_FOUND_FOR_ORDER("1002", "該筆付款ID查無訂單ID");

    private final String code;
    private final String description;

    PaymentErrorCode(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
