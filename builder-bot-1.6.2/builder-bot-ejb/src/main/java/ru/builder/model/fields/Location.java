package ru.builder.model.fields;

import com.google.gson.annotations.SerializedName;

public class Location {

    @SerializedName("geoPointValue")
    private GeoPoint geoPoint;

    public Location(GeoPoint geoPoint) {
        this.geoPoint = geoPoint;
    }

    public GeoPoint getGeoPoint() {
        return geoPoint;
    }
}
