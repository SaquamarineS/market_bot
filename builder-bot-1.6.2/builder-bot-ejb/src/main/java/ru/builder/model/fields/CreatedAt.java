package ru.builder.model.fields;

import com.google.gson.annotations.SerializedName;

public class CreatedAt {

    @SerializedName("timestampValue")
    private String timestampValue;

    public CreatedAt(String timestampValue) {
        this.timestampValue = timestampValue;
    }

    public String getTimestampValue() {
        return timestampValue;
    }

    public void setTimestampValue(String timestampValue) {
        this.timestampValue = timestampValue;
    }
}
