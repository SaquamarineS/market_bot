package ru.builder.model.payment;

import com.google.gson.annotations.SerializedName;

public class Payment {

    @SerializedName("id")
    private String id;
    @SerializedName("status")
    private String status;
    @SerializedName("paid")
    private boolean isPaid;
    @SerializedName("created_at")
    private String createdAt;
    @SerializedName("captured_at")
    private String capturedAt;
    private Confirmation confirmation;
    @SerializedName("payment_method")
    private PaymentMethod paymentMethod;
    @SerializedName("cancellation_details")
    private CancellationDetails cancellationDetails;
    @SerializedName("nextBillDate")
    private String nextBillDate;
    @SerializedName("amount")
    private Amount amount;
    @SerializedName("description")
    private String description;
    private String userId;
    private String messageId;

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Amount getAmount() {
        return amount;
    }

    public void setAmount(Amount amount) {
        this.amount = amount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getNextBillDate() {
        return nextBillDate;
    }

    public void setNextBillDate(String nextBillDate) {
        this.nextBillDate = nextBillDate;
    }

    public CancellationDetails getCancellationDetails() {
        return cancellationDetails;
    }

    public void setCancellationDetails(CancellationDetails cancellationDetails) {
        this.cancellationDetails = cancellationDetails;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isPaid() {
        return isPaid;
    }

    public void setPaid(boolean paid) {
        isPaid = paid;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getCapturedAt() {
        return capturedAt;
    }

    public void setCapturedAt(String capturedAt) {
        this.capturedAt = capturedAt;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public Confirmation getConfirmation() {
        return confirmation;
    }

    public void setConfirmation(Confirmation confirmation) {
        this.confirmation = confirmation;
    }
}
