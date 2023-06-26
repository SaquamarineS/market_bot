package ru.builder.model.order;

import java.io.Serializable;

public class Location implements Serializable {

    private String address;
    private String mapInfo;

    public Location(String address, String mapInfo) {
        this.address = address;
        this.mapInfo = mapInfo;
    }

    public String getAddress() {
        return address;
    }

    public String getMapInfo() {
        return mapInfo;
    }
}
