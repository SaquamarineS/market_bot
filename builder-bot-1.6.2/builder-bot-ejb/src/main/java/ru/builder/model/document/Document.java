package ru.builder.model.document;

import com.google.gson.annotations.SerializedName;

public class Document {

    @SerializedName("fields")
    private Fields fields;

    public Document(Fields fields) {
        this.fields = fields;
    }

    public Fields getFields() {
        return fields;
    }
}
