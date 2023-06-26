package ru.builder.model.fields;

import com.google.gson.annotations.SerializedName;

public class UserPhone {

    @SerializedName("stringValue")
    private String stringValue;

    public UserPhone(String stringValue) {
        this.stringValue = stringValue;
    }

    public String getStringValue() {
        return stringValue;
    }
}
