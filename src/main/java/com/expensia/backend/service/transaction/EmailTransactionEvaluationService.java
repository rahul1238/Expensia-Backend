package com.expensia.backend.service.transaction;

import com.expensia.backend.model.EmailTransaction;
import com.expensia.backend.model.Transaction;
import com.expensia.backend.repository.EmailTransactionRepository;
import com.expensia.backend.repository.TransactionRepository;
import com.expensia.backend.utils.TransactionEnums.Currency;
import com.expensia.backend.utils.TransactionEnums.TransactionMethod;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailTransactionEvaluationService {

    private final EmailTransactionRepository emailTransactionRepository;
    private final TransactionRepository transactionRepository;
    
    @Value("${transaction.evaluation.bank-email-patterns:bank,hdfc,icici,sbi,axis,kotak,paytm,phonepe,googlepay}")
    private String bankEmailPatterns;
    
    private Set<String> bankPatterns;

    @PostConstruct
    private void initializeBankPatterns() {
        if (bankEmailPatterns != null && !bankEmailPatterns.trim().isEmpty()) {
            bankPatterns = Set.of(bankEmailPatterns.toLowerCase().split(","));
            log.info("Initialized {} bank email patterns for transaction evaluation", bankPatterns.size());
        } else {
            bankPatterns = Set.of();
            log.warn("No bank email patterns configured - bank prioritization will be disabled");
        }
    }

    /**
     * Evaluates all pending email transactions and processes them for addition to main transactions
     * 
     * @return EvaluationResult containing statistics about the evaluation process
     */
    @Transactional
    public EvaluationResult evaluateAndProcessEmailTransactions() {
        return evaluateAndProcessEmailTransactions(null);
    }
    
    /**
     * Evaluates pending email transactions for a specific user or all users
     * 
     * @param specificUserId If provided, only process transactions for this user. If null, process all users.
     * @return EvaluationResult containing statistics about the evaluation process
     */
    @Transactional
    public EvaluationResult evaluateAndProcessEmailTransactions(String specificUserId) {
        log.info("Starting email transaction evaluation and processing{}", 
                specificUserId != null ? " for user: " + specificUserId : "");
        
        long startTime = System.currentTimeMillis();
        EvaluationResult result = EvaluationResult.builder()
                .startTime(LocalDateTime.now())
                .build();
        
        try {
            // Get all email transactions that haven't been processed yet
            List<EmailTransaction> pendingEmailTransactions = specificUserId != null 
                    ? getPendingEmailTransactionsForUser(specificUserId)
                    : getAllPendingEmailTransactions();
                    
            log.info("Found {} pending email transactions to evaluate{}", 
                    pendingEmailTransactions.size(),
                    specificUserId != null ? " for user " + specificUserId : "");
            
            if (pendingEmailTransactions.isEmpty()) {
                result.setSuccess(true);
                result.setMessage("No pending email transactions to process");
                return result;
            }
            
            // Process each user's transactions separately for better isolation
            Map<String, List<EmailTransaction>> transactionsByUser = pendingEmailTransactions.stream()
                    .collect(Collectors.groupingBy(EmailTransaction::getUserId));
                    
            for (Map.Entry<String, List<EmailTransaction>> entry : transactionsByUser.entrySet()) {
                String userId = entry.getKey();
                List<EmailTransaction> userEmailTransactions = entry.getValue();
                
                log.info("Processing {} email transactions for user: {}", userEmailTransactions.size(), userId);
                UserEvaluationResult userResult = processUserEmailTransactions(userId, userEmailTransactions);
                
                // Accumulate results
                result.setTotalProcessed(result.getTotalProcessed() + userResult.getProcessed());
                result.setTotalAdded(result.getTotalAdded() + userResult.getAdded());
                result.setTotalDuplicates(result.getTotalDuplicates() + userResult.getDuplicatesFound());
                result.setTotalSkipped(result.getTotalSkipped() + userResult.getSkipped());
                result.getDetails().addAll(userResult.getDetails());
            }
            
            result.setSuccess(true);
            result.setMessage(String.format(
                "Successfully processed %d email transactions. Added: %d, Duplicates: %d, Skipped: %d",
                result.getTotalProcessed(), result.getTotalAdded(), result.getTotalDuplicates(), result.getTotalSkipped()
            ));
            
        } catch (Exception e) {
            log.error("Error during email transaction evaluation", e);
            result.setSuccess(false);
            result.setError("Evaluation failed: " + e.getMessage());
        } finally {
            result.setEndTime(LocalDateTime.now());
            result.setProcessingTimeMs(System.currentTimeMillis() - startTime);
            log.info("Email transaction evaluation completed in {}ms", result.getProcessingTimeMs());
        }
        
        return result;
    }
    
    /**
     * Processes email transactions for a specific user
     */
    private UserEvaluationResult processUserEmailTransactions(String userId, List<EmailTransaction> emailTransactions) {
        UserEvaluationResult result = UserEvaluationResult.builder()
                .userId(userId)
                .build();
                
        try {
            // Get existing main transactions for the user to check for duplicates
            List<Transaction> existingTransactions = transactionRepository.findByUserId(new ObjectId(userId));
            log.debug("User {} has {} existing main transactions", userId, existingTransactions.size());
            
            // Group email transactions by potential duplicates
            List<EmailTransactionGroup> groupedTransactions = groupSimilarEmailTransactions(emailTransactions);
            log.debug("Grouped {} email transactions into {} groups for user {}", 
                    emailTransactions.size(), groupedTransactions.size(), userId);
            
            for (EmailTransactionGroup group : groupedTransactions) {
                ProcessGroupResult groupResult = processEmailTransactionGroup(userId, group, existingTransactions);
                
                result.setProcessed(result.getProcessed() + groupResult.getProcessed());
                result.setAdded(result.getAdded() + groupResult.getAdded());
                result.setDuplicatesFound(result.getDuplicatesFound() + groupResult.getDuplicatesFound());
                result.setSkipped(result.getSkipped() + groupResult.getSkipped());
                result.getDetails().addAll(groupResult.getDetails());
            }
            
        } catch (Exception e) {
            log.error("Error processing email transactions for user: {}", userId, e);
            result.getDetails().add("Error processing user " + userId + ": " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Groups similar email transactions together to identify potential duplicates
     * This handles cases like:
     * 1. Same transaction from multiple sources (Flipkart + HDFC)
     * 2. Multiple notifications for the same transaction
     */
    private List<EmailTransactionGroup> groupSimilarEmailTransactions(List<EmailTransaction> emailTransactions) {
        Map<String, EmailTransactionGroup> groups = new HashMap<>();
        
        for (EmailTransaction emailTx : emailTransactions) {
            // Create a grouping key based on date and amount (with tolerance)
            String groupKey = createGroupingKey(emailTx);
            
            EmailTransactionGroup group = groups.computeIfAbsent(groupKey, k -> 
                EmailTransactionGroup.builder()
                    .groupKey(k)
                    .transactions(new ArrayList<>())
                    .build()
            );
            
            group.getTransactions().add(emailTx);
        }
        
        return new ArrayList<>(groups.values());
    }
    
    /**
     * Creates a grouping key for email transactions to identify potential duplicates
     */
    private String createGroupingKey(EmailTransaction emailTx) {
        // Round amount to 2 decimal places for grouping
        BigDecimal roundedAmount = BigDecimal.valueOf(emailTx.getAmount())
                .setScale(2, RoundingMode.HALF_UP);
        
        return String.format("%s_%s", 
                emailTx.getDate() != null ? emailTx.getDate().toString() : "unknown",
                roundedAmount.toString());
    }
    
    /**
     * Processes a group of similar email transactions
     */
    private ProcessGroupResult processEmailTransactionGroup(String userId, EmailTransactionGroup group, 
                                                           List<Transaction> existingTransactions) {
        ProcessGroupResult result = ProcessGroupResult.builder().build();
        
        List<EmailTransaction> transactions = group.getTransactions();
        result.setProcessed(transactions.size());
        
        if (transactions.isEmpty()) {
            return result;
        }
        
        // Sort by creation time to prioritize older transactions
        transactions.sort(Comparator.comparing(EmailTransaction::getCreatedAt));
        
        // Check if any transaction in this group already exists in main transactions
        boolean existsInMainTransactions = checkIfGroupExistsInMainTransactions(group, existingTransactions);
        
        if (existsInMainTransactions) {
            log.debug("Group {} already exists in main transactions, marking all as duplicates", group.getGroupKey());
            result.setDuplicatesFound(transactions.size());
            
            // Mark all email transactions in this group as processed
            for (EmailTransaction emailTx : transactions) {
                markEmailTransactionAsProcessed(emailTx, "DUPLICATE_MAIN", "Duplicate of existing main transaction");
                result.getDetails().add(String.format("Marked email transaction %s as duplicate of main transaction", 
                        emailTx.getId()));
            }
            
            return result;
        }
        
        // Handle email transaction duplicates within the group
        return processEmailTransactionDuplicates(userId, group, result);
    }
    
    /**
     * Checks if any transaction in the group already exists in main transactions
     */
    private boolean checkIfGroupExistsInMainTransactions(EmailTransactionGroup group, List<Transaction> existingTransactions) {
        for (EmailTransaction emailTx : group.getTransactions()) {
            for (Transaction mainTx : existingTransactions) {
                if (isTransactionDuplicate(emailTx, mainTx)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * Processes duplicates within a group of email transactions
     */
    private ProcessGroupResult processEmailTransactionDuplicates(String userId, EmailTransactionGroup group, 
                                                                ProcessGroupResult result) {
        List<EmailTransaction> transactions = group.getTransactions();
        
        if (transactions.size() == 1) {
            // Single transaction, add it to main transactions
            EmailTransaction emailTx = transactions.get(0);
            Transaction newTransaction = convertEmailToMainTransaction(userId, emailTx);
            
            try {
                Transaction saved = transactionRepository.save(newTransaction);
                markEmailTransactionAsProcessed(emailTx, "ADDED", "Added to main transactions as " + saved.getId());
                
                result.setAdded(1);
                result.getDetails().add(String.format("Added email transaction %s to main transactions as %s", 
                        emailTx.getId(), saved.getId()));
                        
                log.info("Successfully added email transaction {} to main transactions for user {}", 
                        emailTx.getId(), userId);
                        
            } catch (Exception e) {
                log.error("Failed to add email transaction {} to main transactions", emailTx.getId(), e);
                result.setSkipped(1);
                result.getDetails().add(String.format("Failed to add email transaction %s: %s", 
                        emailTx.getId(), e.getMessage()));
            }
            
        } else {
            // Multiple transactions in group - handle duplicates
            log.debug("Processing {} similar email transactions in group {}", transactions.size(), group.getGroupKey());
            
            // Choose the best representative transaction
            EmailTransaction representative = chooseBestRepresentative(transactions);
            
            // Add the representative to main transactions
            Transaction newTransaction = convertEmailToMainTransaction(userId, representative);
            
            try {
                Transaction saved = transactionRepository.save(newTransaction);
                markEmailTransactionAsProcessed(representative, "ADDED", "Added to main transactions as " + saved.getId());
                
                result.setAdded(1);
                result.getDetails().add(String.format("Added representative email transaction %s to main transactions as %s", 
                        representative.getId(), saved.getId()));
                
                // Mark the rest as duplicates
                for (EmailTransaction emailTx : transactions) {
                    if (!emailTx.getId().equals(representative.getId())) {
                        markEmailTransactionAsProcessed(emailTx, "DUPLICATE_EMAIL", 
                                "Duplicate of email transaction " + representative.getId());
                        result.setDuplicatesFound(result.getDuplicatesFound() + 1);
                        result.getDetails().add(String.format("Marked email transaction %s as duplicate of %s", 
                                emailTx.getId(), representative.getId()));
                    }
                }
                
                log.info("Successfully processed group of {} email transactions for user {}, added 1, marked {} as duplicates", 
                        transactions.size(), userId, transactions.size() - 1);
                        
            } catch (Exception e) {
                log.error("Failed to add representative email transaction {} to main transactions", representative.getId(), e);
                result.setSkipped(transactions.size());
                for (EmailTransaction emailTx : transactions) {
                    result.getDetails().add(String.format("Failed to process email transaction %s: %s", 
                            emailTx.getId(), e.getMessage()));
                }
            }
        }
        
        return result;
    }
    
    /**
     * Chooses the best representative from a group of similar email transactions
     * Prioritizes transactions from banks over merchants, and more detailed descriptions
     */
    private EmailTransaction chooseBestRepresentative(List<EmailTransaction> transactions) {
        // Sort by priority: bank emails first, then by description length, then by creation time
        return transactions.stream()
                .max(Comparator
                        .comparing((EmailTransaction t) -> isBankEmail(t.getSourceEmail()) ? 1 : 0)
                        .thenComparing(t -> t.getDescription() != null ? t.getDescription().length() : 0)
                        .thenComparing(EmailTransaction::getCreatedAt))
                .orElse(transactions.get(0));
    }
    
    /**
     * Checks if an email address is from a bank or financial institution
     * Uses configurable patterns from application properties
     */
    private boolean isBankEmail(String email) {
        if (email == null || bankPatterns == null || bankPatterns.isEmpty()) {
            return false;
        }
        
        String lowerEmail = email.toLowerCase();
        return bankPatterns.stream()
                .anyMatch(pattern -> lowerEmail.contains(pattern.trim().toLowerCase()));
    }
    
    /**
     * Checks if an email transaction is a duplicate of a main transaction
     */
    private boolean isTransactionDuplicate(EmailTransaction emailTx, Transaction mainTx) {
        // Check date match (same day)
        if (emailTx.getDate() != null && mainTx.getDate() != null) {
            if (!emailTx.getDate().equals(mainTx.getDate())) {
                return false;
            }
        }
        
        // Check amount match with small tolerance (0.01)
        double amountDiff = Math.abs(emailTx.getAmount() - mainTx.getAmount());
        if (amountDiff > 0.01) {
            return false;
        }
        
        // Check type match
        if (emailTx.getType() != null && mainTx.getType() != null) {
            if (!emailTx.getType().equalsIgnoreCase(mainTx.getType())) {
                return false;
            }
        }
        
        // Check merchant/description similarity
        String emailMerchant = emailTx.getMerchant() != null ? emailTx.getMerchant().toLowerCase() : "";
        String mainDescription = mainTx.getDescription() != null ? mainTx.getDescription().toLowerCase() : "";
        
        // If both have merchant/description, check for similarity
        if (!emailMerchant.isEmpty() && !mainDescription.isEmpty()) {
            return calculateSimilarity(emailMerchant, mainDescription) > 0.6;
        }
        
        // If we reach here, the basic criteria match (date + amount)
        return true;
    }
    
    /**
     * Calculates similarity between two strings using Jaccard similarity
     */
    private double calculateSimilarity(String str1, String str2) {
        Set<String> set1 = new HashSet<>(Arrays.asList(str1.split("\\s+")));
        Set<String> set2 = new HashSet<>(Arrays.asList(str2.split("\\s+")));
        
        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);
        
        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);
        
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }
    
    /**
     * Converts an email transaction to a main transaction
     */
    private Transaction convertEmailToMainTransaction(String userId, EmailTransaction emailTx) {
        return Transaction.builder()
                .userId(new ObjectId(userId))
                .description(emailTx.getDescription() != null ? emailTx.getDescription() : emailTx.getMerchant())
                .amount(emailTx.getAmount())
                .currency(emailTx.getCurrency() != null ? emailTx.getCurrency() : Currency.INR)
                .date(emailTx.getDate() != null ? emailTx.getDate() : LocalDate.now())
                .category(emailTx.getCategory())
                .type(emailTx.getType() != null ? emailTx.getType() : "debit")
                .notes("Auto-added from email: " + (emailTx.getSourceEmail() != null ? emailTx.getSourceEmail() : "unknown"))
                .transactionMethod(emailTx.getTransactionMethod() != null ? emailTx.getTransactionMethod() : TransactionMethod.OTHER)
                .build();
    }
    
    /**
     * Marks an email transaction as processed
     */
    private void markEmailTransactionAsProcessed(EmailTransaction emailTx, String status, String notes) {
        emailTx.setNotes(notes);
        emailTx.setCategory(status); // Using category field to store processing status
        emailTx.setUpdatedAt(LocalDateTime.now());
        emailTransactionRepository.save(emailTx);
    }
    
    /**
     * Gets all email transactions that haven't been processed yet
     */
    private List<EmailTransaction> getAllPendingEmailTransactions() {
        List<EmailTransaction> allEmailTransactions = emailTransactionRepository.findAll();
        
        // Filter out already processed transactions (those with category set to processing status)
        return allEmailTransactions.stream()
                .filter(tx -> tx.getCategory() == null || 
                             (!tx.getCategory().equals("ADDED") && 
                              !tx.getCategory().equals("DUPLICATE_MAIN") && 
                              !tx.getCategory().equals("DUPLICATE_EMAIL")))
                .collect(Collectors.toList());
    }
    
    /**
     * Gets pending email transactions for a specific user
     */
    private List<EmailTransaction> getPendingEmailTransactionsForUser(String userId) {
        List<EmailTransaction> userEmailTransactions = emailTransactionRepository.findByUserId(userId);
        
        // Filter out already processed transactions
        return userEmailTransactions.stream()
                .filter(tx -> tx.getCategory() == null || 
                             (!tx.getCategory().equals("ADDED") && 
                              !tx.getCategory().equals("DUPLICATE_MAIN") && 
                              !tx.getCategory().equals("DUPLICATE_EMAIL")))
                .collect(Collectors.toList());
    }
    
    // Data classes for results
    
    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class EvaluationResult {
        private boolean success;
        private String message;
        private String error;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private long processingTimeMs;
        
        @lombok.Builder.Default
        private int totalProcessed = 0;
        @lombok.Builder.Default
        private int totalAdded = 0;
        @lombok.Builder.Default
        private int totalDuplicates = 0;
        @lombok.Builder.Default
        private int totalSkipped = 0;
        @lombok.Builder.Default
        private List<String> details = new ArrayList<>();
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    private static class UserEvaluationResult {
        private String userId;
        @lombok.Builder.Default
        private int processed = 0;
        @lombok.Builder.Default
        private int added = 0;
        @lombok.Builder.Default
        private int duplicatesFound = 0;
        @lombok.Builder.Default
        private int skipped = 0;
        @lombok.Builder.Default
        private List<String> details = new ArrayList<>();
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    private static class EmailTransactionGroup {
        private String groupKey;
        private List<EmailTransaction> transactions;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    private static class ProcessGroupResult {
        @lombok.Builder.Default
        private int processed = 0;
        @lombok.Builder.Default
        private int added = 0;
        @lombok.Builder.Default
        private int duplicatesFound = 0;
        @lombok.Builder.Default
        private int skipped = 0;
        @lombok.Builder.Default
        private List<String> details = new ArrayList<>();
    }
}
