package ru.builder.model.subscr;

public class Subscriber {

    private String userId;
    private String paymentId;
    private String email;
    private String subscriptionType;
    private String equipmentType;
    private String equipmentName;
    private boolean isAutoPay;
    private int connectCount;
    private String status;
    private String date;
    private long countOfTrialSubscription;

    public Subscriber(String userId, long countOfTrialSubscription) {
        this.userId = userId;
        this.countOfTrialSubscription = countOfTrialSubscription;
    }

    public Subscriber(String userId, String equipmentType, String status) {
        this.userId = userId;
        this.equipmentType = equipmentType;
        this.status = status;
    }

    public Subscriber(String userId, String equipmentType, String status, String date) {
        this.userId = userId;
        this.equipmentType = equipmentType;
        this.status = status;
        this.date = date;
    }

    public Subscriber(String userId, String equipmentType, int connectCount) {
        this.userId = userId;
        this.equipmentType = equipmentType;
        this.connectCount = connectCount;
    }

    public Subscriber(String userId, String equipmentType, int connectCount, String status, String paymentId, boolean isAutoPay) {
        this.userId = userId;
        this.equipmentType = equipmentType;
        this.connectCount = connectCount;
        this.status = status;
        this.paymentId = paymentId;
        this.isAutoPay = isAutoPay;
    }

    public Subscriber(String userId, String paymentId, String email, String subscriptionType, String equipmentType) {
        this.userId = userId;
        this.paymentId = paymentId;
        this.email = email;
        this.subscriptionType = subscriptionType;
        this.equipmentType = equipmentType;
    }

    public Subscriber(String userId, String paymentId, String email, String subscriptionType, String equipmentType, boolean isAutoPay) {
        this.userId = userId;
        this.paymentId = paymentId;
        this.email = email;
        this.subscriptionType = subscriptionType;
        this.equipmentType = equipmentType;
        this.isAutoPay = isAutoPay;
    }

    public Subscriber(String userId, String paymentId, String email, String subscriptionType, String equipmentType, boolean isAutoPay, String status) {
        this.userId = userId;
        this.paymentId = paymentId;
        this.email = email;
        this.subscriptionType = subscriptionType;
        this.equipmentType = equipmentType;
        this.isAutoPay = isAutoPay;
        this.status = status;
    }

    public void setPaymentId(String paymentId) {
        this.paymentId = paymentId;
    }

    public String getUserId() {
        return userId;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public String getEmail() {
        return email;
    }

    public String getSubscriptionType() {
        return subscriptionType;
    }

    public String getEquipmentType() {
        return equipmentType;
    }

    public boolean isAutoPay() {
        return isAutoPay;
    }

    public int getConnectCount() {
        return connectCount;
    }

    public String getStatus() {
        return status;
    }

    public void setEquipmentName(String equipmentName) {
        this.equipmentName = equipmentName;
    }

    public String getEquipmentName() {
        return equipmentName;
    }

    public long getCountOfTrialSubscription() {
        return countOfTrialSubscription;
    }

    public String getDate() {
        return date;
    }
}
