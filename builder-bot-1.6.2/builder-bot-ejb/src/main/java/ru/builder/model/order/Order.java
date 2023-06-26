package ru.builder.model.order;

import java.io.Serializable;

public class Order implements Serializable {

    private String documentId;
    private String createdAt;
    private String comment;
    private String paymentMethod;
    private Equipment equipment;
    private User user;
    private Location location;

    public Order(String documentId, String createdAt, String comment, String paymentMethod, Equipment equipment, User user, Location location) {
        this.documentId = documentId;
        this.createdAt = createdAt;
        this.comment = comment;
        this.paymentMethod = paymentMethod;
        this.equipment = equipment;
        this.user = user;
        this.location = location;
    }

    public String getDocumentId() {
        return documentId;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public String getComment() {
        return comment;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public Equipment getEquipment() {
        return equipment;
    }

    public User getUser() {
        return user;
    }

    public Location getLocation() {
        return location;
    }
}
