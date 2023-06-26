package ru.builder.model.payment;

import com.google.gson.annotations.SerializedName;

public class PaymentMethod {

    @SerializedName("type")
    private String type;
    @SerializedName("id")
    private String id;
    @SerializedName("saved")
    private boolean isSaved;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public boolean isSaved() {
        return isSaved;
    }

    public void setSaved(boolean saved) {
        isSaved = saved;
    }
}
