package ru.builder.model.order;

import java.io.Serializable;

public class Equipment implements Serializable {

    private String id;
    private String name;

    public Equipment(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
