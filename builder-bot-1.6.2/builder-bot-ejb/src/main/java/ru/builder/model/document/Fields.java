package ru.builder.model.document;

import com.google.gson.annotations.SerializedName;
import ru.builder.model.fields.*;

public class Fields {

    @SerializedName("userName")
    private UserName userName;
    @SerializedName("createdAt")
    private CreatedAt createdAt;
    @SerializedName("userPhone")
    private UserPhone userPhone;
    @SerializedName("comment")
    private Comment comment;
    @SerializedName("ownerId")
    private OwnerId ownerId;
    @SerializedName("address")
    private Address address;
    @SerializedName("typeID")
    private TypeID typeID;
    @SerializedName("location")
    private Location location;
    @SerializedName("paymentMethod")
    private PaymentMethod paymentMethod;
    @SerializedName("id")
    private Id id;
    @SerializedName("name")
    private Name name;

    public Fields(UserName userName, CreatedAt createdAt, UserPhone userPhone, Comment comment, OwnerId ownerId, Address address, TypeID typeID, Location location, PaymentMethod paymentMethod, Id id) {
        this.userName = userName;
        this.createdAt = createdAt;
        this.userPhone = userPhone;
        this.comment = comment;
        this.ownerId = ownerId;
        this.address = address;
        this.typeID = typeID;
        this.location = location;
        this.paymentMethod = paymentMethod;
        this.id = id;
    }

    public UserName getUserName() {
        return userName;
    }

    public CreatedAt getCreatedAt() {
        return createdAt;
    }

    public UserPhone getUserPhone() {
        return userPhone;
    }

    public Comment getComment() {
        return comment;
    }

    public OwnerId getOwnerId() {
        return ownerId;
    }

    public Address getAddress() {
        return address;
    }

    public TypeID getTypeID() {
        return typeID;
    }

    public Location getLocation() {
        return location;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public Id getId() {
        return id;
    }

    public Name getName() {
        return name;
    }
}
