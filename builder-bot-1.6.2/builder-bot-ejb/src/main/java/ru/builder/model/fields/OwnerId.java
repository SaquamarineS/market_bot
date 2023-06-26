package ru.builder.model.fields;

import com.google.gson.annotations.SerializedName;

public class OwnerId {

    @SerializedName("stringValue")
    private String stringValue;

    public OwnerId(String stringValue) {
        this.stringValue = stringValue;
    }

    public String getStringValue() {
        return stringValue;
    }
}
