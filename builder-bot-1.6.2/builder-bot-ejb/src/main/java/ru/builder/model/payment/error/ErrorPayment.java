package ru.builder.model.payment.error;

public class ErrorPayment {

    String errorDate;
    int tryCount;

    public ErrorPayment(String errorDate, int tryCount) {
        this.errorDate = errorDate;
        this.tryCount = tryCount;
    }

    public String getErrorDate() {
        return errorDate;
    }

    public int getTryCount() {
        return tryCount;
    }
}
