package com.spares.app.network.model;

public class SafeGoldBuyRequest {

    public double amount;
    public String currency = "INR";
    public String idempotencyKey;

    public SafeGoldBuyRequest(double amount, String idempotencyKey) {
        this.amount = amount;
        this.idempotencyKey = idempotencyKey;
    }
}
