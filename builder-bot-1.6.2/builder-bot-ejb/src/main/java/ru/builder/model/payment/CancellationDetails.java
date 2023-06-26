package ru.builder.model.payment;

public class CancellationDetails {

    private String party;
    private String reason;

    CancellationDetails() {
    }

    public CancellationDetails(String party, String reason) {
        this.party = party;
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}
