package com.spares.app.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Spares SMS Parsing Engine
 * Implements the full inclusion/exclusion keyword matrix and round-up formula from PRD §4.2
 */
public class SmsParser {

    // ── Inclusion keywords (at least one must match) ──────────────────────────
    private static final String[] INCLUSION_KEYWORDS = {
        "debited", "spent", "vpa", "txnd", "used at", "amount paid"
    };

    // ── Exclusion / blacklist keywords (any match = discard) ─────────────────
    private static final String[] EXCLUSION_KEYWORDS = {
        "otp", "code", "secret", "credited", "received", "failed"
    };

    // ── Amount extraction patterns (₹, Rs, INR formats) ──────────────────────
    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
        "(?:rs\\.?|inr|₹)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)",
        Pattern.CASE_INSENSITIVE
    );

    // Fallback: plain number with decimal in a debit context
    private static final Pattern AMOUNT_FALLBACK = Pattern.compile(
        "([0-9]{2,}(?:\\.[0-9]{1,2})?)\\s*(?:(?:has been|is|was)\\s*)?(?:debited|spent|paid)",
        Pattern.CASE_INSENSITIVE
    );

    public static class ParseResult {
        public final boolean valid;
        public final double originalAmount;
        public final double roundUpAmount;
        public final String reason; // why it was rejected (for logging)

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

    /**
     * Main parse entry point. Returns a ParseResult indicating validity + amounts.
     */
    public static ParseResult parse(String smsBody) {
        if (smsBody == null || smsBody.trim().isEmpty()) {
            return ParseResult.rejected("Empty body");
        }

        String body = smsBody.toLowerCase();

        // ── Step 1: Check exclusion keywords first ────────────────────────────
        for (String excl : EXCLUSION_KEYWORDS) {
            if (body.contains(excl)) {
                return ParseResult.rejected("Exclusion keyword hit: " + excl);
            }
        }

        // ── Step 2: Require at least one inclusion keyword ────────────────────
        boolean included = false;
        for (String incl : INCLUSION_KEYWORDS) {
            if (body.contains(incl)) {
                included = true;
                break;
            }
        }
        if (!included) {
            return ParseResult.rejected("No inclusion keyword found");
        }

        // ── Step 3: Extract the transaction amount ────────────────────────────
        double amount = extractAmount(smsBody);
        if (amount <= 0) {
            return ParseResult.rejected("No valid amount found");
        }

        // ── Step 4: Apply round-up formula: R = (⌈A/10⌉ × 10) − A ──────────
        double roundUp = computeRoundUp(amount);

        return ParseResult.accepted(amount, roundUp);
    }

    /**
     * Extracts the first monetary amount from the message body.
     */
    private static double extractAmount(String body) {
        try {
            // Primary: look for ₹/Rs/INR prefix
            Matcher m = AMOUNT_PATTERN.matcher(body);
            if (m.find()) {
                String raw = m.group(1).replace(",", "");
                return Double.parseDouble(raw);
            }
            // Fallback: number immediately before/after debit verb
            m = AMOUNT_FALLBACK.matcher(body);
            if (m.find()) {
                String raw = m.group(1).replace(",", "");
                return Double.parseDouble(raw);
            }
        } catch (NumberFormatException e) {
            // fall through
        }
        return -1;
    }

    /**
     * PRD §4.2 formula: R = (⌈A/10⌉ × 10) − A
     * Special case: if A is already a multiple of 10, R = 10 (not 0).
     *
     * Uses epsilon-safe modulo check to avoid double precision traps
     * (e.g. 120.0000000001 % 10 != 0 in raw float arithmetic).
     */
    public static double computeRoundUp(double amount) {
        // Epsilon-safe check: round to 2 decimal places first (amounts are always XX.XX)
        double rounded = Math.round(amount * 100.0) / 100.0;
        long paise = Math.round(rounded * 100); // work in integer paise
        if (paise % 1000 == 0) { // paise multiple of 1000 = rupee multiple of 10
            return 10.00;
        }
        double ceiling = Math.ceil(rounded / 10.0) * 10.0;
        double r = ceiling - rounded;
        r = Math.max(0.01, Math.min(10.00, r));
        return Math.round(r * 100.0) / 100.0;
    }
}
