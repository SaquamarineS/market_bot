package ru.builder.model.payment;

import com.google.gson.annotations.SerializedName;

public class Confirmation {

    @SerializedName("type")
    private String type;
    @SerializedName("return_url")
    private String returnUrl;
    @SerializedName("confirmation_url")
    private String confirmationUrl;

    public Confirmation() {

    }

    public Confirmation(String type, String returnUrl) {
        this.type = type;
        this.returnUrl = returnUrl;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getReturnUrl() {
        return returnUrl;
    }

    public void setReturnUrl(String returnUrl) {
        this.returnUrl = returnUrl;
    }

    public String getConfirmationUrl() {
        return confirmationUrl;
    }

    public void setConfirmationUrl(String confirmationUrl) {
        this.confirmationUrl = confirmationUrl;
    }
}
