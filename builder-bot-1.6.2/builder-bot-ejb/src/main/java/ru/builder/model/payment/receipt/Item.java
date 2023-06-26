package ru.builder.model.payment.receipt;

import com.google.gson.annotations.SerializedName;
import ru.builder.model.payment.Amount;

public class Item {

    private String description;
    private String quantity;
    private Amount amount;
    @SerializedName("vat_code")
    private int vatCode;

    public Item() {

    }

    public Item(String description, String quantity, Amount amount, int vatCode) {
        this.description = description;
        this.quantity = quantity;
        this.amount = amount;
        this.vatCode = vatCode;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getQuantity() {
        return quantity;
    }

    public void setQuantity(String quantity) {
        this.quantity = quantity;
    }

    public Amount getAmount() {
        return amount;
    }

    public void setAmount(Amount amount) {
        this.amount = amount;
    }

    public int getVatCode() {
        return vatCode;
    }

    public void setVatCode(int vatCode) {
        this.vatCode = vatCode;
    }
}
