package com.example.inventory.exception;

import lombok.Getter;

@Getter
public class InventoryException extends RuntimeException {

    private final InventoryErrorCode inventoryErrorCode;
    private final String errorMsg;
    private final String productId;

    public InventoryException(InventoryErrorCode inventoryErrorCode, String errorMsg, String productId) {
        super(inventoryErrorCode.getDescription());
        this.inventoryErrorCode = inventoryErrorCode;
        this.errorMsg = errorMsg;
        this.productId = productId;
    }

    public static InventoryException of(InventoryErrorCode code, String productId) {
        return new InventoryException(code, code.getDescription(), productId);
    }

    public static InventoryException of(InventoryErrorCode code) {
        return new InventoryException(code, code.getDescription(), null);
    }
}
