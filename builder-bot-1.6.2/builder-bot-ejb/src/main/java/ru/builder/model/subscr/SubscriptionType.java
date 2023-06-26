package ru.builder.model.subscr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public enum SubscriptionType {

    week("week"),
    month("month"),
    year("year"),
    test("test");

    private final String subscriptionType;

    SubscriptionType(String value) {
        this.subscriptionType = value;
    }

    public String value() {
        return name();
    }

    public static SubscriptionType fromValue(String v) {
        return valueOf(v);
    }

    public static List<String> getAllSubscriptionsType() {
        return new ArrayList<>(Arrays.asList("week", "month", "year", "test"));
    }
}
