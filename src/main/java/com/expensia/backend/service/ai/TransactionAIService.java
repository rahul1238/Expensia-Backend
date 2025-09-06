package com.expensia.backend.service.ai;

import com.expensia.backend.config.BankDomainConfig;
import com.expensia.backend.model.EmailTransaction;
import com.expensia.backend.utils.TransactionEnums.Currency;
import com.expensia.backend.utils.TransactionEnums.TransactionMethod;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpStatusCodeException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionAIService {

    private static final Pattern TYPE_DEBIT = Pattern.compile("(?i)\\b(debited|spent|withdrawn|deducted|paid|purchase|bill|emi|autopay)\\b");
    private static final Pattern TYPE_CREDIT = Pattern.compile("(?i)\\b(credited|received|deposit|refund|cashback|reward)\\b");
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("(?i)(INR|Rs\\.?|USD|\\$|₹)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)|([0-9,]+(?:\\.[0-9]{1,2})?)\\s*(INR|Rs\\.?|USD|\\$|₹)");
    private static final Pattern MERCHANT_AFTER_AT = Pattern.compile("(?i)(?:\\bat\\b|\\bto\\b|\\bin\\b|\\bfrom\\b|\\bvia\\b)\\s+([A-Za-z0-9 &._-]{2,60})");
    private static final Pattern DATE_PATTERN = Pattern.compile("(?i)\\b(\\d{1,2}[-/ ](?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec|\\d{1,2})[-/ ]\\d{2,4}|\\d{4}-\\d{2}-\\d{2}|\\d{2}/\\d{2}/\\d{4})\\b");
    // Enhanced positive and negative heuristics
    private static final Pattern POSITIVE_TERMS = Pattern.compile("(?i)\\b(debited|credited|transaction|txn|payment|purchase|spent|withdrawn|transfer|imps|neft|upi|pos|card|paid|settled|bill|emi|wallet|deposit|refund)\\b");
    private static final Pattern NEGATIVE_TERMS = Pattern.compile("(?i)\\b(balance|available balance|closing balance|a/c balance|statement|e-statement|voucher|gift\\s*card|coupon|promo\\s*code|offer\\s*code|promo|offer|otp|password|verification|login|security|alert\\s*only)\\b");

    // Heuristics for transaction method
    private static final Pattern UPI_TERMS = Pattern.compile("(?i)\\b(upi|vpa|bhim|gpay|google pay|phonepe|paytm upi)\\b");
    private static final Pattern CARD_TERMS = Pattern.compile("(?i)\\b(card|credit card|debit card|pos|swipe|terminal)\\b");
    private static final Pattern CREDIT_CARD_TERMS = Pattern.compile("(?i)\\b(credit card)\\b");
    private static final Pattern DEBIT_CARD_TERMS = Pattern.compile("(?i)\\b(debit card)\\b");
    private static final Pattern BANK_TRANSFER_TERMS = Pattern.compile("(?i)\\b(neft|imps|rtgs|bank transfer|fund transfer)\\b");
    private static final Pattern NET_BANKING_TERMS = Pattern.compile("(?i)\\b(net banking|internet banking)\\b");
    private static final Pattern CASH_TERMS = Pattern.compile("(?i)\\b(atm withdrawal|cash withdrawal|cash)\\b");
    private static final Pattern PAYPAL_TERMS = Pattern.compile("(?i)\\b(paypal)\\b");
    // Utility pattern: extract integer seconds from strings like "46s" or "2.5s" (captures integer part)
    private static final Pattern RETRY_SECONDS = Pattern.compile("([0-9]+)");

    private final RestTemplate restTemplate = createRestTemplate();
    // Reuse a single, thread-safe ObjectMapper instance
    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();

    @Value("${ai.gemini.api.url:https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash-latest:generateContent}")
    private String geminiUrl;
    @Value("${ai.gemini.api.key:}")
    private String geminiApiKey;
    // Simple in-memory cooldown when API returns 429
    private volatile long aiCooldownUntilMs = 0L;
    private volatile long lastAiCooldownLogMs = 0L;

    private final BankDomainConfig bankDomainConfig;

    public Optional<EmailTransaction> evaluate(String userId, String messageId, String subject, String body, String sender, String dateHeader) {
        // Accept only bank-originated alerts as requested
        if (!isBankSender(sender)) {
            log.debug("Rejected sender (not bank): {}", sender);
            return Optional.empty();
        }
        log.info("Evaluating transaction from sender: {} subject: {}", sender, subject);
        String text = safe(subject) + "\n" + safe(body);

    String type = null;
    if (TYPE_DEBIT.matcher(text).find()) type = "debit";
    if (TYPE_CREDIT.matcher(text).find()) type = "credit";

    Double amount = extractAmount(text);
        String merchant = extractMerchant(text, sender);
        LocalDate date = extractDate(text, dateHeader);
    TransactionMethod method = detectMethod(text);

        boolean hasPositive = POSITIVE_TERMS.matcher(text).find();
        boolean hasNegative = NEGATIVE_TERMS.matcher(text).find();

        // Hard block known non-transactional content like vouchers/promos/statements/balance alerts
        if (hasNegative && (type == null || text.toLowerCase().matches(".*(voucher|gift\\s*card|coupon|promo|offer).*"))) {
            log.info("Rejected due to negative terms or non-transactional content: {}", subject);
            return Optional.empty();
        }

        if (amount != null && date != null && StringUtils.hasText(merchant) && (type != null || hasPositive)) {
            EmailTransaction tx = baseTx(userId, messageId, sender, subject, amount, date, type, merchant, method);
            tx.setUniqueHash(computeHash(userId, amount, date, merchant));
            log.info("Transaction created from regex: {} - {} - {} - {}", amount, date, merchant, type);
            return Optional.of(tx);
        }

        log.info("Regex failed, attempting AI fallback for: {}", subject);

        // Fallback to AI only if configured and not in cooldown
        if (!StringUtils.hasText(geminiApiKey)) {
            log.info("AI not configured, skipping: {}", subject);
            return Optional.empty();
        }
        long now = System.currentTimeMillis();
        if (now < aiCooldownUntilMs) {
            // skip AI calls during cooldown
            log.info("AI in cooldown, skipping: {}", subject);
            return Optional.empty();
        }

        try {
            Map<String, Object> parsed = callGemini(subject, body, sender, dateHeader);
            Double amt = toDouble(parsed.get("amount"));
            LocalDate dt = toDate((String) parsed.get("date"));
            String merch = (String) parsed.get("merchant");
            String tp = (String) parsed.getOrDefault("type", type);
            TransactionMethod parsedMethod = toMethod((String) parsed.get("method"));
            Object isTxn = parsed.get("isTransaction");
            if (isTxn instanceof Boolean && !((Boolean) isTxn)) {
                log.info("AI determined not a transaction: {}", subject);
                return Optional.empty();
            }

            if (amt == null || dt == null || !StringUtils.hasText(merch)) {
                log.info("AI parsing incomplete - amount: {}, date: {}, merchant: {} for: {}", amt, dt, merch, subject);
                return Optional.empty();
            }

        EmailTransaction tx = baseTx(userId, messageId, sender, subject, amt, dt, tp, merch, parsedMethod != null ? parsedMethod : detectMethod(subject + "\n" + body));
            tx.setUniqueHash(computeHash(userId, amt, dt, merch));
            log.info("Transaction created from AI: {} - {} - {} - {}", amt, dt, merch, tp);
            return Optional.of(tx);
        } catch (Exception e) {
            log.warn("AI fallback failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private EmailTransaction baseTx(String userId, String messageId, String sender, String subject,
                    Double amount, LocalDate date, String type, String merchant,
                    TransactionMethod method) {
        EmailTransaction tx = EmailTransaction.builder()
                .userId(userId)
                .messageId(messageId)
                .sourceEmail(sender)
                .merchant(merchant)
                .description(subject)
                .amount(amount)
                .currency(Currency.INR) // default; can be improved with detection later
                .date(date)
                .type(type != null ? type : "debit")
        .transactionMethod(method != null ? method : TransactionMethod.OTHER)
                .build();
        return tx;
    }

    private Double extractAmount(String text) {
        Matcher m = AMOUNT_PATTERN.matcher(text);
        if (m.find()) {
            // Check both capture groups for amount
            String raw = m.group(2) != null ? m.group(2) : m.group(3);
            if (raw != null) {
                raw = raw.replace(",", "");
                try { return Double.parseDouble(raw); } catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }

    private String extractMerchant(String text, String sender) {
        Matcher m = MERCHANT_AFTER_AT.matcher(text);
        if (m.find()) {
            String name = m.group(1).trim();
            // strip trailing punctuation
            name = name.replaceAll("[.,;:]+$", "");
            return name;
        }
        if (StringUtils.hasText(sender) && sender.contains("@")) {
            String domain = sender.substring(sender.indexOf('@') + 1);
            return domain.replaceFirst("^www\\.", "");
        }
        return null;
    }

    private LocalDate extractDate(String text, String header) {
        // Try header first
        LocalDate d = toDate(header);
        if (d != null) return d;
        // Try free text date
        Matcher m = DATE_PATTERN.matcher(text);
        if (m.find()) {
            d = toDate(m.group(1));
        }
        if (d != null) return d;
        return LocalDate.now(ZoneId.systemDefault());
    }

    private LocalDate toDate(String s) {
        if (!StringUtils.hasText(s)) return null;
        List<DateTimeFormatter> fmts = List.of(
                DateTimeFormatter.RFC_1123_DATE_TIME, // typical email header
                DateTimeFormatter.ofPattern("d-MMM-uuuu").withLocale(Locale.ENGLISH),
                DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("d/M/uuuu"),
                DateTimeFormatter.ofPattern("d-M-uuuu")
        );
        for (DateTimeFormatter f : fmts) {
            try {
                return java.time.ZonedDateTime.parse(s, f).toLocalDate();
            } catch (DateTimeParseException ignored) {}
            try {
                return LocalDate.parse(s, f);
            } catch (DateTimeParseException ignored) {}
        }
        return null;
    }

    private Double toDouble(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).doubleValue();
        try { return Double.parseDouble(o.toString()); } catch (Exception e) { return null; }
    }

    private Map<String, Object> callGemini(String subject, String body, String sender, String dateHeader) {
    String prompt = "You are a financial email classifier and parser. Determine if the email describes a real money transaction (card/UPI/NEFT/IMPS/POS/bank credit/debit). " +
        "Ignore balance alerts, statements, OTP, vouchers, coupons, promo/offer codes, rewards, or marketing content. " +
        "Return a JSON: { isTransaction: boolean, type: 'credit'|'debit'|null, amount: number|null, merchant: string|null, date: 'yyyy-MM-dd'|null, method: 'CASH'|'CREDIT_CARD'|'DEBIT_CARD'|'BANK_TRANSFER'|'UPI'|'NET_BANKING'|'PAYPAL'|'OTHER'|null }. " +
        "Subject: '" + safe(subject) + "'. Sender: '" + safe(sender) + "'. Date header: '" + safe(dateHeader) + "'. Body: '" + safe(body) + "'. " +
        "If not a transaction, set isTransaction=false and others null. Return minified JSON only.";

        Map<String, Object> payload = Map.of(
                "contents", List.of(Map.of(
                        "parts", List.of(Map.of("text", prompt))
                ))
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String url = geminiUrl + "?key=" + geminiApiKey;
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(payload, headers);
        ResponseEntity<String> resp;
        try {
            resp = restTemplate.postForEntity(url, req, String.class);
        } catch (HttpStatusCodeException ex) {
            handleGeminiNon2xx(ex.getStatusCode().value(), ex.getResponseHeaders(), ex.getResponseBodyAsString());
            throw new RuntimeException(ex.getStatusCode() + " " + ex.getMessage());
        }

        if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
            handleGeminiNon2xx(resp);
            throw new RuntimeException("Gemini call failed: " + resp.getStatusCode());
        }

        // Parse Gemini response structure
        // Expecting something like: { candidates: [ { content: { parts: [ { text: "{...json...}" } ] } } ] }
        final Map<String, Object> root;
        try {
            @SuppressWarnings("unchecked") Map<String, Object> tmp = JSON.readValue(resp.getBody(), Map.class);
            root = tmp;
        } catch (Exception e) {
            throw new RuntimeException("AI result parse failed: " + e.getMessage());
        }
        Object candidates = root.get("candidates");
        if (!(candidates instanceof List) || ((List<?>) candidates).isEmpty()) {
            throw new RuntimeException("No AI candidates");
        }
        Object first = ((List<?>) candidates).get(0);
        if (!(first instanceof Map)) throw new RuntimeException("Bad AI response");
        Object content = ((Map<?, ?>) first).get("content");
        if (!(content instanceof Map)) throw new RuntimeException("Bad AI content");
        Object parts = ((Map<?, ?>) content).get("parts");
        if (!(parts instanceof List) || ((List<?>) parts).isEmpty()) throw new RuntimeException("No AI parts");
        Object part0 = ((List<?>) parts).get(0);
        if (!(part0 instanceof Map)) throw new RuntimeException("Bad AI part");
        Object text = ((Map<?, ?>) part0).get("text");
        if (!(text instanceof String)) throw new RuntimeException("No AI text");

        String json = (String) text;
        try {
            // best effort: strip code fences if any
            json = json.trim();
            if (json.startsWith("```") ) {
                int idx = json.indexOf('{');
                int last = json.lastIndexOf('}');
                if (idx >= 0 && last > idx) json = json.substring(idx, last + 1);
            }
            @SuppressWarnings("unchecked") Map<String, Object> out = JSON.readValue(json, Map.class);
            return out;
        } catch (Exception e) {
            throw new RuntimeException("AI JSON parse failed: " + e.getMessage());
        }
    }

    private void handleGeminiNon2xx(ResponseEntity<String> resp) {
        HttpStatusCode code = resp.getStatusCode();
        handleGeminiNon2xx(code != null ? code.value() : -1, resp.getHeaders(), resp.getBody());
    }

    private void handleGeminiNon2xx(int statusCode, HttpHeaders headers, String body) {
        if (statusCode == 429) {
            int seconds = 60; // default
            // Try to parse Retry-After header
            List<String> retryAfter = headers != null ? headers.get("Retry-After") : null;
            if (retryAfter != null && !retryAfter.isEmpty()) {
                try { seconds = Integer.parseInt(retryAfter.get(0)); } catch (Exception ignored) {}
            }
            // Try to parse JSON retry info
        if (body != null) {
                try {
            @SuppressWarnings("unchecked") Map<String, Object> root = JSON.readValue(body, Map.class);
                    Object err = root.get("error");
                    if (err instanceof Map) {
                        Object details = ((Map<?,?>) err).get("details");
                        if (details instanceof List) {
                            for (Object d : (List<?>) details) {
                                if (d instanceof Map) {
                                    Object t = ((Map<?,?>) d).get("@type");
                                    if (t instanceof String && ((String) t).toLowerCase(Locale.ROOT).contains("retryinfo")) {
                                        Object rd = ((Map<?,?>) d).get("retryDelay");
                                        if (rd instanceof String) {
                                            String s = ((String) rd).trim();
                                            // e.g., "46s" or "2.5s"
                                            java.util.regex.Matcher m = RETRY_SECONDS.matcher(s);
                                            if (m.find()) {
                                                seconds = Integer.parseInt(m.group(1));
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
            }
            long until = System.currentTimeMillis() + seconds * 1000L;
            aiCooldownUntilMs = Math.max(aiCooldownUntilMs, until);
            // Log cooldown start once per window
            long now = System.currentTimeMillis();
            if (now - lastAiCooldownLogMs > 5_000) {
                lastAiCooldownLogMs = now;
                log.info("Gemini API rate-limited; cooling down for ~{}s", seconds);
            }
        }
    }

    private RestTemplate createRestTemplate() {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(10_000);
        return new RestTemplate(factory);
    }

    public String computeHash(String userId, Double amount, LocalDate date, String merchant) {
        try {
            String input = String.format(Locale.ROOT, "%s|%.2f|%s|%s",
                    safe(userId), amount == null ? 0.0 : amount,
                    date != null ? date.toString() : "", safe(merchant));
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("Hashing failed", e);
        }
    }

    private String safe(String s) { return s == null ? "" : s; }

    private boolean isBankSender(String senderEmail) {
        if (!StringUtils.hasText(senderEmail) || !senderEmail.contains("@")) return false;
        String rawDomain = senderEmail.substring(senderEmail.indexOf('@') + 1).toLowerCase(Locale.ROOT);
        // Strip common subdomains more comprehensively
        final String domain = rawDomain.replaceFirst("^(mail|alerts|no-reply|noreply|notifications?|updates?|info|support|service)\\.", "");
        Set<String> banks = bankDomainConfig.getDomains();
        if (banks == null || banks.isEmpty()) return false;
        // Check both exact match and subdomain match
        return banks.stream().anyMatch(bankDomain -> 
            domain.equals(bankDomain) || domain.endsWith("." + bankDomain)
        );
    }

    private TransactionMethod detectMethod(String text) {
        if (text == null) return TransactionMethod.OTHER;
        String t = text.toLowerCase(Locale.ROOT);
        if (UPI_TERMS.matcher(t).find()) return TransactionMethod.UPI;
        if (PAYPAL_TERMS.matcher(t).find()) return TransactionMethod.PAYPAL;
        if (CASH_TERMS.matcher(t).find()) return TransactionMethod.CASH;
        if (BANK_TRANSFER_TERMS.matcher(t).find()) return TransactionMethod.BANK_TRANSFER;
        if (NET_BANKING_TERMS.matcher(t).find()) return TransactionMethod.NET_BANKING;
        if (CREDIT_CARD_TERMS.matcher(t).find()) return TransactionMethod.CREDIT_CARD;
        if (DEBIT_CARD_TERMS.matcher(t).find()) return TransactionMethod.DEBIT_CARD;
        if (CARD_TERMS.matcher(t).find()) return TransactionMethod.DEBIT_CARD; // default to card spend as debit card
        return TransactionMethod.OTHER;
    }

    private TransactionMethod toMethod(String s) {
        if (!StringUtils.hasText(s)) return null;
        String key = s.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        try {
            return TransactionMethod.valueOf(key);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
