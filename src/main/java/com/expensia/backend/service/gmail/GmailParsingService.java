package com.expensia.backend.service.gmail;

import com.expensia.backend.model.EmailTransaction;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GmailParsingService {

    // Very simple parsers for amounts, dates, and merchant names from common bank SMS/email patterns
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("(?i)(INR|Rs\\.?|USD|\\$)[\\s]*([0-9,]+(?:\\.[0-9]{1,2})?)");
    private static final Pattern MERCHANT_PATTERN = Pattern.compile("(?i)(at|to|in)\\s+([A-Za-z0-9 &._-]{2,50})");

    public Optional<EmailTransaction> parse(String userId, String messageId, String fromEmail, String subject, String snippet) {
        if (subject == null) subject = "";
        if (snippet == null) snippet = "";
        String text = subject + "\n" + snippet;

        Matcher amountMatcher = AMOUNT_PATTERN.matcher(text);
        Double amount = null;
        if (amountMatcher.find()) {
            String raw = amountMatcher.group(2).replace(",", "");
            try { amount = Double.parseDouble(raw); } catch (NumberFormatException ignored) {}
        }

        if (amount == null) {
            return Optional.empty();
        }

        String merchant = null;
        Matcher merchMatcher = MERCHANT_PATTERN.matcher(text);
        if (merchMatcher.find()) {
            merchant = merchMatcher.group(2).trim();
        }
        if (merchant == null || merchant.isBlank()) {
            merchant = fromEmail != null ? fromEmail : "Unknown";
        }

    Instant now = Instant.now(); // fallback
    LocalDate date = now.atZone(ZoneId.systemDefault()).toLocalDate();

    EmailTransaction tx = EmailTransaction.builder()
                .userId(userId)
                .messageId(messageId)
                .sourceEmail(fromEmail)
                .merchant(merchant)
        .description(subject)
                .amount(amount)
                .date(date)
        .type("debit")
                .build();
        return Optional.of(tx);
    }
}
