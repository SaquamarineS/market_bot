package ru.builder.model.document;

import com.google.gson.annotations.SerializedName;

public class DocumentResponse {

    @SerializedName("document")
    private Document document;

    public Document getDocument() {
        return document;
    }
}
