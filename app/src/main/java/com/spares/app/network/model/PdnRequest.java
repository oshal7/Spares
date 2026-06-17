package com.spares.app.network.model;

public class PdnRequest {

    public String mandateId;
    public double amount;
    public String debitDate;

    public PdnRequest(String mandateId, double amount, String debitDate) {
        this.mandateId = mandateId;
        this.amount = amount;
        this.debitDate = debitDate;
    }
}
