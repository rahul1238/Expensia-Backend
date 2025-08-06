package com.expensia.backend.type;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Comprehensive user statistics containing both basic and detailed transaction information
 * Supports backward compatibility with existing simple statistics while providing rich data
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserStatistics {
    
    // Basic Statistics (for backward compatibility)
    private String userId;
    private double totalIncome;
    private double totalExpenses;
    private double netSavings;
    private int transactionCount;
    
    // User Profile Information (for detailed statistics)
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private LocalDateTime accountCreatedAt;
    private LocalDateTime lastUpdatedAt;
    
    // Transaction Count Statistics
    private long totalTransactions;
    private long transactionsThisMonth;
    private long transactionsThisYear;
    private long transactionsToday;
    
    // Amount Statistics
    private double totalAmount;
    private double totalAmountThisMonth;
    private double totalAmountThisYear;
    private double totalAmountToday;
    private double averageTransactionAmount;
    private double largestTransactionAmount;
    private double smallestTransactionAmount;
    
    // Transaction Type Breakdown
    private Map<String, Long> transactionsByType;
    private Map<String, Double> amountsByType;
    
    // Category Breakdown
    private Map<String, Long> transactionsByCategory;
    private Map<String, Double> amountsByCategory;
    
    // Transaction Method Breakdown
    private Map<String, Long> transactionsByMethod;
    private Map<String, Double> amountsByMethod;
    
    // Currency Breakdown
    private Map<String, Long> transactionsByCurrency;
    private Map<String, Double> amountsByCurrency;
    
    // Time-based Statistics
    private LocalDate firstTransactionDate;
    private LocalDate mostRecentTransactionDate;
    private LocalDate mostActiveDay;
    private long mostActiveDayCount;
    
    // Recent Activity
    private LocalDateTime lastTransactionDateTime;
    private String lastTransactionDescription;
    private double lastTransactionAmount;
    
    // Account Age
    private long accountAgeInDays;
    private double averageTransactionsPerDay;
    
    // Monthly Trends (last 12 months)
    private Map<String, Long> monthlyTransactionCounts;
    private Map<String, Double> monthlyTransactionAmounts;

    /**
     * Constructor for basic statistics (backward compatibility)
     */
    public UserStatistics(String userId, double totalIncome, double totalExpenses, double netSavings, int transactionCount) {
        this.userId = userId;
        this.totalIncome = totalIncome;
        this.totalExpenses = totalExpenses;
        this.netSavings = netSavings;
        this.transactionCount = transactionCount;
        this.totalTransactions = transactionCount;
        this.totalAmount = totalIncome + totalExpenses;
    }
}
