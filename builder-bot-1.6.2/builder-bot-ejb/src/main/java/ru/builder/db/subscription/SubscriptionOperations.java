package ru.builder.db.subscription;

import ru.builder.model.subscr.Subscription;

import java.util.List;

public interface SubscriptionOperations {

    void createTable();

    Subscription getSubscriptionInfo(String subscriptionType, String equipmentType);

    List<Subscription> getAllSubscriptions(String equipmentId);
}