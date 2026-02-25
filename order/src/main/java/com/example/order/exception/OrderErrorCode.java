package com.example.order.exception;

import lombok.Getter;

@Getter
public enum OrderErrorCode {
    SUCCESS("0000", "執行成功"),

    ORDERID_NOT_FOUND("1001", "無此帳號");

    private final String code;
    private final String description;

    OrderErrorCode(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
