package ru.builder.model.payment;

import com.google.gson.annotations.SerializedName;

public class PaymentMethodData {

    @SerializedName("type")
    private String type;

    public PaymentMethodData(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
