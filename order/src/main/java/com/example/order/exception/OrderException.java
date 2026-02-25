package com.example.order.exception;

import lombok.Getter;

@Getter
public class OrderException extends RuntimeException {
    private OrderErrorCode orderErrorCode;
    private String errorMsg;
    private String orderId;

    public OrderException(OrderErrorCode orderErrorCode, String errorMsg, String admUrl) {
        super(orderErrorCode.getDescription());
        this.orderErrorCode = orderErrorCode;
        this.errorMsg = errorMsg;
        this.orderId = orderId;
    }

    public static OrderException of(OrderErrorCode code, String orderId) {
        return new OrderException(code, code.getDescription(), "orderId:" + orderId);
    }

    public static OrderException of(OrderErrorCode code) {
        return new OrderException(code, code.getDescription(), null);
    }
}
