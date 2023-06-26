package ru.builder.model.payment;

import com.google.gson.annotations.SerializedName;
import ru.builder.model.payment.receipt.Receipt;

public class CreatePayment extends Payment {

    @SerializedName("payment_method_data")
    private PaymentMethodData paymentMethodData;
    @SerializedName("payment_method_id")
    private String paymentMethodId;
    @SerializedName("save_payment_method")
    private String savePaymentMethod;
    @SerializedName("capture")
    private String capture;
    private int tryCount;
    @SerializedName("receipt")
    private Receipt receipt;

    public CreatePayment() {
    }

    public CreatePayment(PaymentMethodData paymentMethodData, String savePaymentMethod, String capture, Receipt receipt) {
        this.paymentMethodData = paymentMethodData;
        this.savePaymentMethod = savePaymentMethod;
        this.capture = capture;
        this.receipt = receipt;
    }

    public CreatePayment(String paymentMethodId, String capture, Receipt receipt) {
        this.paymentMethodId = paymentMethodId;
        this.capture = capture;
        this.receipt = receipt;
    }

    public int getTryCount() {
        return tryCount;
    }

    public void setTryCount(int tryCount) {
        this.tryCount = tryCount;
    }

    public String getPaymentMethodId() {
        return paymentMethodId;
    }
}
