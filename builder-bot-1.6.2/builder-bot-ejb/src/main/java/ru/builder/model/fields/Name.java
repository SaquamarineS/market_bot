package ru.builder.model.fields;

import com.google.gson.annotations.SerializedName;

public class Name {

    @SerializedName("stringValue")
    private String stringValue;

    public Name(String stringValue) {
        this.stringValue = stringValue;
    }

    public String getStringValue() {
        return stringValue;
    }
}
