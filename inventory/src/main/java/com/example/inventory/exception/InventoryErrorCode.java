package com.example.inventory.exception;

import lombok.Getter;

@Getter
public enum InventoryErrorCode {
    SUCCESS("0000", "執行成功"),

    PRODUCT_NOT_FOUND("1001", "查無此商品庫存"),
    INSUFFICIENT_INVENTORY("1002", "庫存不足");

    private final String code;
    private final String description;

    InventoryErrorCode(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
