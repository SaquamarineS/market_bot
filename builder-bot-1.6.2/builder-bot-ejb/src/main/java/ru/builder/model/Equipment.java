package ru.builder.model;

public class Equipment {

    private String id;
    private String name;
    private String duration;
    private long countOfSubscription;

    public Equipment(String id, String duration, long countOfSubscription) {
        this.id = id;
        this.duration = duration;
        this.countOfSubscription = countOfSubscription;
    }

    public Equipment(String id, long countOfSubscription) {
        this.id = id;
        this.countOfSubscription = countOfSubscription;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public long getCountOfSubscription() {
        return countOfSubscription;
    }

    public String getDuration() {
        return duration;
    }

    public void setName(String name) {
        this.name = name;
    }
}
