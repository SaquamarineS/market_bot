package ru.builder.model.subscr;

public class Subscription {

    private String amount;
    private String description;
    private SubscriptionType subscriptionType;
    private int duration;

    public Subscription(String amount, String description, SubscriptionType subscriptionType, int duration) {
        this.amount = amount;
        this.description = description;
        this.subscriptionType = subscriptionType;
        this.duration = duration;
    }

    public String getAmount() {
        return amount;
    }

    public String getDescription() {
        return description;
    }

    public SubscriptionType getSubscriptionType() {
        return subscriptionType;
    }

    public int getDuration() {
        return duration;
    }
}
