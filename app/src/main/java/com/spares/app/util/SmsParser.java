package com.spares.app.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SmsParser {

    // ── Hard-reject: transaction did not happen ───────────────────────────────
    private static final String[] FAILED_KEYWORDS = {
        "transaction failed", "failed due to",
        "insufficient funds", "insufficient balance",
        "payment declined", "payment unsuccessful",
        "transaction unsuccessful", "could not be processed",
        "transaction not", "txn failed",
    };

    // ── Non-transaction noise (OTP, balance alerts) ───────────────────────────
    private static final String[] NON_TRANSACTION_KEYWORDS = {
        "otp", "one time password", "secret code",
        "balance alert", "low balance", "minimum balance",
        "a/c balance is", "acct balance", "your balance",
    };

    // ── CREDIT: money coming in (record but no round-up) ─────────────────────
    private static final String[] CREDIT_KEYWORDS = {
        "credited", "received", "refunded", "refund",
        "reversed", "cashback", "reward", "money added",
        "deposited", "transfer credited",
    };

    // ── TRANSFER: bank-to-bank transfers (round-up applies) ──────────────────
    private static final String[] TRANSFER_KEYWORDS = {
        "neft", "rtgs", "imps", "fund transfer",
    };

    // ── DEBIT: regular spend keywords ────────────────────────────────────────
    private static final String[] DEBIT_KEYWORDS = {
        "debited", "spent", "vpa", "txnd",
        "used at", "amount paid", "deducted",
        "paid to", "paid successfully",
        "money transferred", "transferred to",
        "payment of", "has been paid", " paid",
    };

    // ── Amount extraction ─────────────────────────────────────────────────────
    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
        "(?:rs\\.?|inr|₹)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern AMOUNT_FALLBACK = Pattern.compile(
        "([0-9]{2,}(?:\\.[0-9]{1,2})?)\\s*(?:(?:has been|is|was)\\s*)?(?:debited|spent|paid|deducted)",
        Pattern.CASE_INSENSITIVE
    );

    // ── Merchant extraction ───────────────────────────────────────────────────
    private static final Pattern MERCHANT_VPA = Pattern.compile(
        "(?:paid to|to vpa|vpa(?:\\s+is)?)\\s+([a-z0-9._-]+)@",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern MERCHANT_AT = Pattern.compile(
        "\\bat\\s+([A-Z][A-Za-z0-9 &.']{2,28}?)(?:[,./\\-]|\\s{2}|$)",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern MERCHANT_TO = Pattern.compile(
        "(?:paid to|payment to|transfer to|to)\\s+([A-Za-z][A-Za-z0-9 &.']{2,28}?)(?:[,./\\-]|\\s{2}|$)",
        Pattern.CASE_INSENSITIVE
    );

    // ─────────────────────────────────────────────────────────────────────────

    public static class ParseResult {
        public final boolean valid;
        public final double originalAmount;
        public final double roundUpAmount;
        public final String category;  // "DEBIT", "CREDIT", "TRANSFER"
        public final String merchant;
        public final String reason;

        private ParseResult(boolean valid, double originalAmount, double roundUpAmount,
                            String category, String merchant, String reason) {
            this.valid = valid;
            this.originalAmount = originalAmount;
            this.roundUpAmount = roundUpAmount;
            this.category = category;
            this.merchant = merchant;
            this.reason = reason;
        }

        public static ParseResult rejected(String reason) {
            return new ParseResult(false, 0, 0, null, null, reason);
        }

        public static ParseResult accepted(double original, double roundUp, String category, String merchant) {
            return new ParseResult(true, original, roundUp, category, merchant, null);
        }
    }

    public static ParseResult parse(String smsBody) {
        if (smsBody == null || smsBody.trim().isEmpty()) {
            return ParseResult.rejected("Empty body");
        }

        String body = smsBody.toLowerCase();

        // 1. Hard-reject failed transactions
        for (String kw : FAILED_KEYWORDS) {
            if (body.contains(kw)) {
                return ParseResult.rejected("Failed transaction: " + kw);
            }
        }

        // 2. Filter noise (OTPs, balance alerts)
        for (String kw : NON_TRANSACTION_KEYWORDS) {
            if (body.contains(kw)) {
                return ParseResult.rejected("Non-transaction: " + kw);
            }
        }

        // 3. Detect category
        String category = detectCategory(body);
        if (category == null) {
            return ParseResult.rejected("No transaction keyword found");
        }

        // 4. Extract amount
        double amount = extractAmount(smsBody);
        if (amount <= 0) {
            return ParseResult.rejected("Amount not found");
        }

        // 5. For DEBIT/TRANSFER: skip round numbers (exact multiples of ₹10)
        if (!"CREDIT".equals(category)) {
            long paise = Math.round(amount * 100);
            if (paise % 1000 == 0) {
                return ParseResult.rejected("Round number — no spare");
            }
        }

        // 6. Extract merchant name
        String merchant = extractMerchant(smsBody);

        // 7. Compute round-up (only for DEBIT/TRANSFER; credits get 0)
        double roundUp = "CREDIT".equals(category) ? 0.0 : computeRoundUp(amount);

        return ParseResult.accepted(amount, roundUp, category, merchant);
    }

    private static String detectCategory(String body) {
        for (String kw : CREDIT_KEYWORDS) {
            if (body.contains(kw)) return "CREDIT";
        }
        for (String kw : TRANSFER_KEYWORDS) {
            if (body.contains(kw)) return "TRANSFER";
        }
        for (String kw : DEBIT_KEYWORDS) {
            if (body.contains(kw)) return "DEBIT";
        }
        return null;
    }

    private static double extractAmount(String body) {
        try {
            Matcher m = AMOUNT_PATTERN.matcher(body);
            if (m.find()) return Double.parseDouble(m.group(1).replace(",", ""));
            m = AMOUNT_FALLBACK.matcher(body);
            if (m.find()) return Double.parseDouble(m.group(1).replace(",", ""));
        } catch (NumberFormatException ignored) {}
        return -1;
    }

    static String extractMerchant(String body) {
        // 1. VPA-based (most reliable): "paid to swiggy.orders@icici"
        Matcher m = MERCHANT_VPA.matcher(body.toLowerCase());
        if (m.find()) {
            return cleanMerchantName(m.group(1));
        }

        // 2. "at MERCHANT" pattern
        m = MERCHANT_AT.matcher(body);
        if (m.find()) {
            String candidate = m.group(1).trim();
            if (!isGenericWord(candidate.toLowerCase())) {
                return cleanMerchantName(candidate);
            }
        }

        // 3. "paid to / transfer to MERCHANT" pattern
        m = MERCHANT_TO.matcher(body);
        if (m.find()) {
            String candidate = m.group(1).trim();
            if (!isGenericWord(candidate.toLowerCase())) {
                return cleanMerchantName(candidate);
            }
        }

        return "";
    }

    private static String cleanMerchantName(String raw) {
        // Remove known UPI suffixes
        raw = raw.replaceAll("(?i)\\.(orders|pay|new|merchant|upi|pos|retail|in|com)$", "");
        // Replace separators with spaces
        raw = raw.replace(".", " ").replace("_", " ").replace("-", " ");
        // Strip leading/trailing digits
        raw = raw.replaceAll("^[0-9\\s]+|[0-9\\s]+$", "").trim();
        if (raw.isEmpty()) return "";
        // Title-case
        String[] words = raw.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(" ");
            sb.append(Character.toUpperCase(w.charAt(0)));
            if (w.length() > 1) sb.append(w.substring(1).toLowerCase());
        }
        return sb.toString().trim();
    }

    private static boolean isGenericWord(String word) {
        String[] generic = {
            "your", "the ", "a ", "an ", "bank", "account", "acct",
            "upi", "vpa", "rs", "inr", "ref", "txn", "transaction",
        };
        for (String g : generic) {
            if (word.startsWith(g)) return true;
        }
        return false;
    }

    public static double computeRoundUp(double amount) {
        double rounded = Math.round(amount * 100.0) / 100.0;
        double ceiling = Math.ceil(rounded / 10.0) * 10.0;
        double r = ceiling - rounded;
        r = Math.max(0.01, Math.min(10.00, r));
        return Math.round(r * 100.0) / 100.0;
    }
}
