package ru.builder.model.payment.receipt;

import java.util.ArrayList;
import java.util.List;

public class Receipt {

    private Customer customer;
    private List<Item> items;

    public Receipt() {
    }

    public Receipt(Customer customer, List<Item> items) {
        this.customer = customer;
        this.items = items;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public void setItems(List<Item> items) {
        this.items = items;
    }

    public List<Item> getItems() {
        if (this.items == null) {
            return new ArrayList<>();
        }
        return this.items;
    }
}
