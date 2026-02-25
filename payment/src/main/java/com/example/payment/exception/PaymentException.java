package com.example.payment.exception;

import lombok.Getter;
@Getter
public class PaymentException extends RuntimeException{
    private PaymentErrorCode paymentErrorCode;
    private String errorMsg;
    private String paymentId;

    public PaymentException(PaymentErrorCode paymentErrorCode, String errorMsg, String admUrl) {
        super(paymentErrorCode.getDescription());
        this.paymentErrorCode = paymentErrorCode;
        this.errorMsg = errorMsg;
        this.paymentId = paymentId;
    }

    public static PaymentException of(PaymentErrorCode code, String paymentId) {
        return new PaymentException(code, code.getDescription(), "paymentId:" + paymentId);
    }

    public static PaymentException of(PaymentErrorCode code) {
        return new PaymentException(code, code.getDescription(), null);
    }

}
