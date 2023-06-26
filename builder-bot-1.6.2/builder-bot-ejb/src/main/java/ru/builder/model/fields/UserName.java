package ru.builder.model.fields;

import com.google.gson.annotations.SerializedName;

public class UserName {

    @SerializedName("stringValue")
    private String stringValue;

    public UserName(String stringValue) {
        this.stringValue = stringValue;
    }

    public String getStringValue() {
        return stringValue;
    }
}
