package ru.builder.model.fields;

import com.google.gson.annotations.SerializedName;

public class TypeID {

    @SerializedName("stringValue")
    private String stringValue;

    public TypeID(String stringValue) {
        this.stringValue = stringValue;
    }

    public String getStringValue() {
        return stringValue;
    }
}
