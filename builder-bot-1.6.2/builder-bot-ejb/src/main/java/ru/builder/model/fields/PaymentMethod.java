package ru.builder.model.fields;

import com.google.gson.annotations.SerializedName;

public class PaymentMethod {

    @SerializedName("stringValue")
    private String stringValue;

    public PaymentMethod(String stringValue) {
        this.stringValue = stringValue;
    }

    public String getStringValue() {
        return stringValue;
    }
}
