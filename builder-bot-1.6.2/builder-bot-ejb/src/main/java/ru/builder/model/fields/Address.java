package ru.builder.model.fields;

import com.google.gson.annotations.SerializedName;

public class Address {

    @SerializedName("stringValue")
    private String stringValue;

    public Address(String stringValue) {
        this.stringValue = stringValue;
    }

    public String getStringValue() {
        return stringValue;
    }
}
