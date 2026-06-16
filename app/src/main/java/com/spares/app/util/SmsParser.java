package com.spares.app.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Spares SMS Parsing Engine
 *
 * Expanded keyword coverage for all major Indian banks and UPI apps:
 * HDFC, ICICI, SBI, Axis, Kotak, Yes Bank, IDFC, BoB, Bajaj,
 * PhonePe, GPay, Paytm, Airtel Money, wallets.
 */
public class SmsParser {

    // ── Inclusion keywords — at least one must match ──────────────────────────
    // Ordered from most specific to least to minimise false positives
    private static final String[] INCLUSION_KEYWORDS = {
        // Original PRD keywords
        "debited",
        "spent",
        "vpa",
        "txnd",
        "used at",
        "amount paid",
        // Added: covers Kotak, Bajaj EMI, IDFC ("deducted from account")
        "deducted",
        // Added: covers GPay/Paytm ("paid to merchant"), PhonePe ("paid successfully")
        "paid to",
        "paid successfully",
        // Added: covers HDFC UPI new style ("money transferred")
        "money transferred",
        // Added: Airtel Money, wallet transfers ("transferred to merchant")
        "transferred to",
        // Added: wallet payments ("payment of Rs.X done to")
        "payment of",
        // Added: generic ("has been paid")
        "has been paid",
        // Added: catches "You have paid Rs.X to" and postpaid bills
        // Space prefix prevents matching "prepaid" — no space before 'paid' in that word
        " paid",
    };

    // ── Exclusion keywords — any match = discard immediately ─────────────────
    private static final String[] EXCLUSION_KEYWORDS = {
        "otp",
        "secret",
        "credited",
        "received",
        "failed",
        // Balance inquiry / low balance alerts (no transaction)
        "balance alert",
        "low balance",
        "minimum balance",
        "a/c balance is",
        "acct balance",
    };

    // ── Amount extraction: ₹/Rs/INR prefix pattern ────────────────────────────
    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
        "(?:rs\\.?|inr|₹)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)",
        Pattern.CASE_INSENSITIVE
    );

    // ── Fallback: number before/after a debit verb ────────────────────────────
    private static final Pattern AMOUNT_FALLBACK = Pattern.compile(
        "([0-9]{2,}(?:\\.[0-9]{1,2})?)\\s*(?:(?:has been|is|was)\\s*)?(?:debited|spent|paid|deducted)",
        Pattern.CASE_INSENSITIVE
    );

    // ─────────────────────────────────────────────────────────────────────────

    public static class ParseResult {
        public final boolean valid;
        public final double originalAmount;
        public final double roundUpAmount;
        public final String reason;

        private ParseResult(boolean valid, double originalAmount, double roundUpAmount, String reason) {
            this.valid = valid;
            this.originalAmount = originalAmount;
            this.roundUpAmount = roundUpAmount;
            this.reason = reason;
        }

        public static ParseResult rejected(String reason) {
            return new ParseResult(false, 0, 0, reason);
        }

        public static ParseResult accepted(double original, double roundUp) {
            return new ParseResult(true, original, roundUp, null);
        }
    }

    public static ParseResult parse(String smsBody) {
        if (smsBody == null || smsBody.trim().isEmpty()) {
            return ParseResult.rejected("Empty body");
        }

        String body = smsBody.toLowerCase();

        // Step 1: Exclusion check (fast-fail)
        for (String excl : EXCLUSION_KEYWORDS) {
            if (body.contains(excl)) {
                return ParseResult.rejected("Blocked keyword: " + excl);
            }
        }

        // Step 2: Inclusion check
        boolean matched = false;
        String matchedKeyword = null;
        for (String incl : INCLUSION_KEYWORDS) {
            if (body.contains(incl)) {
                matched = true;
                matchedKeyword = incl.trim();
                break;
            }
        }
        if (!matched) {
            return ParseResult.rejected("No debit keyword found");
        }

        // Step 3: Extract amount
        double amount = extractAmount(smsBody);
        if (amount <= 0) {
            return ParseResult.rejected("Amount not found (keyword matched: '" + matchedKeyword + "')");
        }

        // Step 4: Round-up formula
        return ParseResult.accepted(amount, computeRoundUp(amount));
    }

    private static double extractAmount(String body) {
        try {
            Matcher m = AMOUNT_PATTERN.matcher(body);
            if (m.find()) {
                return Double.parseDouble(m.group(1).replace(",", ""));
            }
            m = AMOUNT_FALLBACK.matcher(body);
            if (m.find()) {
                return Double.parseDouble(m.group(1).replace(",", ""));
            }
        } catch (NumberFormatException ignored) {}
        return -1;
    }

    /**
     * PRD §4.2: R = (⌈A/10⌉ × 10) − A
     * If A is an exact multiple of 10 → R = 10 (never 0).
     * Uses integer paise arithmetic to avoid float precision traps.
     */
    public static double computeRoundUp(double amount) {
        double rounded = Math.round(amount * 100.0) / 100.0;
        long paise = Math.round(rounded * 100);
        if (paise % 1000 == 0) return 10.00; // exact multiple of ₹10
        double ceiling = Math.ceil(rounded / 10.0) * 10.0;
        double r = ceiling - rounded;
        r = Math.max(0.01, Math.min(10.00, r));
        return Math.round(r * 100.0) / 100.0;
    }
}
