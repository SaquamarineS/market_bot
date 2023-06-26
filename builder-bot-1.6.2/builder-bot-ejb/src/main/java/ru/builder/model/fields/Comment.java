package ru.builder.model.fields;

import com.google.gson.annotations.SerializedName;

public class Comment {

    @SerializedName("stringValue")
    private String stringValue;

    public Comment(String stringValue) {
        this.stringValue = stringValue;
    }

    public String getStringValue() {
        return stringValue;
    }
}
