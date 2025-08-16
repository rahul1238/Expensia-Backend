package com.expensia.backend.service;

import com.expensia.backend.exception.TransactionServiceException;
import com.expensia.backend.model.Transaction;
import com.expensia.backend.model.User;
import com.expensia.backend.repository.TransactionRepository;
import com.expensia.backend.type.UserStatistics;
import com.expensia.backend.utils.AuthUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final TransactionRepository transactionRepository;
    private final AuthUser authUser;

    public UserStatistics getUserStatistics() {
        try {
            User currentUser = authUser.getCurrentUser();
            ObjectId userId = new ObjectId(currentUser.getId());
            
            List<Transaction> allTransactions = transactionRepository.findByUserId(userId);
            
            if (allTransactions.isEmpty()) {
                return new UserStatistics(currentUser.getId(), 0.0, 0.0, 0.0, 0);
            }
            
            double totalIncome = 0.0;
            double totalExpenses = 0.0;
            
            for (Transaction transaction : allTransactions) {
                double amount = transaction.getAmount();
                String type = transaction.getType();
                
                if (type != null && type.equalsIgnoreCase("credit")) {
                    totalIncome += amount;
                } else {
                    totalExpenses += amount;
                }
            }
            
            double netSavings = totalIncome - totalExpenses;
            int transactionCount = allTransactions.size();
            
            return new UserStatistics(currentUser.getId(), totalIncome, totalExpenses, netSavings, transactionCount);
            
        } catch (IllegalArgumentException e) {
            log.error("Invalid user ID format during statistics calculation", e);
            throw new TransactionServiceException(
                TransactionServiceException.TransactionErrorType.AUTHENTICATION_ERROR,
                "Invalid user ID format",
                "getUserStatistics",
                e
            );
        } catch (DataAccessException e) {
            log.error("Database error during statistics calculation", e);
            throw new TransactionServiceException(
                TransactionServiceException.TransactionErrorType.DATABASE_ERROR,
                "Failed to retrieve user statistics from database",
                "getUserStatistics",
                e
            );
        } catch (Exception e) {
            log.error("Unexpected error calculating user statistics", e);
            throw new TransactionServiceException(
                TransactionServiceException.TransactionErrorType.UNKNOWN_ERROR,
                "Unexpected error calculating user statistics",
                "getUserStatistics",
                e
            );
        }
    }

    public UserStatistics getDetailedUserStatistics() {
        try {
            User currentUser = authUser.getCurrentUser();
            ObjectId userId = new ObjectId(currentUser.getId());
            
            List<Transaction> allTransactions = transactionRepository.findByUserId(userId);
            
            UserStatistics.UserStatisticsBuilder statsBuilder = UserStatistics.builder()
                .userId(currentUser.getId())
                .username(currentUser.getUsername())
                .email(currentUser.getEmail())
                .firstName(currentUser.getFirstName())
                .lastName(currentUser.getLastName())
                .accountCreatedAt(currentUser.getCreatedAt())
                .lastUpdatedAt(currentUser.getUpdatedAt());
            
            if (allTransactions.isEmpty()) {
                return buildEmptyStatistics(statsBuilder, currentUser);
            }
            
            LocalDate today = LocalDate.now();
            LocalDate startOfMonth = today.withDayOfMonth(1);
            LocalDate startOfYear = today.withDayOfYear(1);
            LocalDateTime startOfToday = today.atStartOfDay();
            LocalDateTime endOfToday = today.plusDays(1).atStartOfDay();
            
            List<Transaction> todayTransactions = filterTransactionsByDateRange(allTransactions, startOfToday, endOfToday);
            List<Transaction> thisMonthTransactions = filterTransactionsByDate(allTransactions, startOfMonth, null);
            List<Transaction> thisYearTransactions = filterTransactionsByDate(allTransactions, startOfYear, null);
            
            statsBuilder
                .totalTransactions(allTransactions.size())
                .transactionsToday(todayTransactions.size())
                .transactionsThisMonth(thisMonthTransactions.size())
                .transactionsThisYear(thisYearTransactions.size());
            
            calculateAmountStatistics(statsBuilder, allTransactions, todayTransactions, thisMonthTransactions, thisYearTransactions);
            calculateTypeBreakdowns(statsBuilder, allTransactions);
            calculateCategoryBreakdowns(statsBuilder, allTransactions);
            calculateMethodBreakdowns(statsBuilder, allTransactions);
            calculateCurrencyBreakdowns(statsBuilder, allTransactions);
            calculateTimeBasedStatistics(statsBuilder, allTransactions, currentUser);
            calculateRecentActivity(statsBuilder, allTransactions);
            calculateMonthlyTrends(statsBuilder, allTransactions);
            
            return statsBuilder.build();
            
        } catch (IllegalArgumentException e) {
            log.error("Invalid user ID format during detailed statistics calculation", e);
            throw new TransactionServiceException(
                TransactionServiceException.TransactionErrorType.AUTHENTICATION_ERROR,
                "Invalid user ID format",
                "getDetailedUserStatistics",
                e
            );
        } catch (DataAccessException e) {
            log.error("Database error during detailed statistics calculation", e);
            throw new TransactionServiceException(
                TransactionServiceException.TransactionErrorType.DATABASE_ERROR,
                "Failed to retrieve detailed user statistics from database",
                "getDetailedUserStatistics",
                e
            );
        } catch (Exception e) {
            log.error("Unexpected error calculating detailed user statistics", e);
            throw new TransactionServiceException(
                TransactionServiceException.TransactionErrorType.UNKNOWN_ERROR,
                "Unexpected error calculating detailed user statistics",
                "getDetailedUserStatistics",
                e
            );
        }
    }

    private UserStatistics buildEmptyStatistics(UserStatistics.UserStatisticsBuilder statsBuilder, User user) {
        long accountAgeInDays = user.getCreatedAt() != null ? 
            ChronoUnit.DAYS.between(user.getCreatedAt().toLocalDate(), LocalDate.now()) : 0;
            
        return statsBuilder
            .totalTransactions(0)
            .transactionsToday(0)
            .transactionsThisMonth(0)
            .transactionsThisYear(0)
            .totalAmount(0.0)
            .totalAmountToday(0.0)
            .totalAmountThisMonth(0.0)
            .totalAmountThisYear(0.0)
            .averageTransactionAmount(0.0)
            .largestTransactionAmount(0.0)
            .smallestTransactionAmount(0.0)
            .transactionsByType(new HashMap<>())
            .amountsByType(new HashMap<>())
            .transactionsByCategory(new HashMap<>())
            .amountsByCategory(new HashMap<>())
            .transactionsByMethod(new HashMap<>())
            .amountsByMethod(new HashMap<>())
            .transactionsByCurrency(new HashMap<>())
            .amountsByCurrency(new HashMap<>())
            .accountAgeInDays(accountAgeInDays)
            .averageTransactionsPerDay(0.0)
            .monthlyTransactionCounts(new HashMap<>())
            .monthlyTransactionAmounts(new HashMap<>())
            .build();
    }

    private List<Transaction> filterTransactionsByDateRange(List<Transaction> transactions, LocalDateTime start, LocalDateTime end) {
        return transactions.stream()
            .filter(t -> t.getCreatedAt() != null)
            .filter(t -> !t.getCreatedAt().isBefore(start))
            .filter(t -> t.getCreatedAt().isBefore(end))
            .collect(Collectors.toList());
    }

    private List<Transaction> filterTransactionsByDate(List<Transaction> transactions, LocalDate startDate, LocalDate endDate) {
        return transactions.stream()
            .filter(t -> t.getDate() != null)
            .filter(t -> !t.getDate().isBefore(startDate))
            .filter(t -> endDate == null || !t.getDate().isAfter(endDate))
            .collect(Collectors.toList());
    }

    private void calculateAmountStatistics(UserStatistics.UserStatisticsBuilder statsBuilder,
                                         List<Transaction> allTransactions,
                                         List<Transaction> todayTransactions,
                                         List<Transaction> thisMonthTransactions,
                                         List<Transaction> thisYearTransactions) {
        
        double totalAmount = allTransactions.stream().mapToDouble(Transaction::getAmount).sum();
        double todayAmount = todayTransactions.stream().mapToDouble(Transaction::getAmount).sum();
        double monthAmount = thisMonthTransactions.stream().mapToDouble(Transaction::getAmount).sum();
        double yearAmount = thisYearTransactions.stream().mapToDouble(Transaction::getAmount).sum();
        
        OptionalDouble largest = allTransactions.stream().mapToDouble(Transaction::getAmount).max();
        OptionalDouble smallest = allTransactions.stream().mapToDouble(Transaction::getAmount).min();
        double average = allTransactions.isEmpty() ? 0.0 : totalAmount / allTransactions.size();
        
        double totalIncome = 0.0;
        double totalExpenses = 0.0;
        
        for (Transaction transaction : allTransactions) {
            double amount = transaction.getAmount();
            String type = transaction.getType();
            
            if (type != null && type.equalsIgnoreCase("credit")) {
                totalIncome += amount;
            } else {
                totalExpenses += amount;
            }
        }
        
        statsBuilder
            .totalAmount(totalAmount)
            .totalAmountToday(todayAmount)
            .totalAmountThisMonth(monthAmount)
            .totalAmountThisYear(yearAmount)
            .averageTransactionAmount(average)
            .largestTransactionAmount(largest.orElse(0.0))
            .smallestTransactionAmount(smallest.orElse(0.0))
            .totalIncome(totalIncome)
            .totalExpenses(totalExpenses)
            .netSavings(totalIncome - totalExpenses)
            .transactionCount((int) allTransactions.size());
    }

    private void calculateTypeBreakdowns(UserStatistics.UserStatisticsBuilder statsBuilder, List<Transaction> transactions) {
        Map<String, Long> countsByType = transactions.stream()
            .filter(t -> t.getType() != null)
            .collect(Collectors.groupingBy(Transaction::getType, Collectors.counting()));
            
        Map<String, Double> amountsByType = transactions.stream()
            .filter(t -> t.getType() != null)
            .collect(Collectors.groupingBy(Transaction::getType, 
                Collectors.summingDouble(Transaction::getAmount)));
        
        statsBuilder
            .transactionsByType(countsByType)
            .amountsByType(amountsByType);
    }

    private void calculateCategoryBreakdowns(UserStatistics.UserStatisticsBuilder statsBuilder, List<Transaction> transactions) {
        Map<String, Long> countsByCategory = transactions.stream()
            .filter(t -> t.getCategory() != null)
            .collect(Collectors.groupingBy(Transaction::getCategory, Collectors.counting()));
            
        Map<String, Double> amountsByCategory = transactions.stream()
            .filter(t -> t.getCategory() != null)
            .collect(Collectors.groupingBy(Transaction::getCategory, 
                Collectors.summingDouble(Transaction::getAmount)));
        
        statsBuilder
            .transactionsByCategory(countsByCategory)
            .amountsByCategory(amountsByCategory);
    }

    private void calculateMethodBreakdowns(UserStatistics.UserStatisticsBuilder statsBuilder, List<Transaction> transactions) {
        Map<String, Long> countsByMethod = transactions.stream()
            .filter(t -> t.getTransactionMethod() != null)
            .collect(Collectors.groupingBy(t -> t.getTransactionMethod().name(), Collectors.counting()));
            
        Map<String, Double> amountsByMethod = transactions.stream()
            .filter(t -> t.getTransactionMethod() != null)
            .collect(Collectors.groupingBy(t -> t.getTransactionMethod().name(), 
                Collectors.summingDouble(Transaction::getAmount)));
        
        statsBuilder
            .transactionsByMethod(countsByMethod)
            .amountsByMethod(amountsByMethod);
    }

    private void calculateCurrencyBreakdowns(UserStatistics.UserStatisticsBuilder statsBuilder, List<Transaction> transactions) {
        Map<String, Long> countsByCurrency = transactions.stream()
            .filter(t -> t.getCurrency() != null)
            .collect(Collectors.groupingBy(t -> t.getCurrency().name(), Collectors.counting()));
            
        Map<String, Double> amountsByCurrency = transactions.stream()
            .filter(t -> t.getCurrency() != null)
            .collect(Collectors.groupingBy(t -> t.getCurrency().name(), 
                Collectors.summingDouble(Transaction::getAmount)));
        
        statsBuilder
            .transactionsByCurrency(countsByCurrency)
            .amountsByCurrency(amountsByCurrency);
    }

    private void calculateTimeBasedStatistics(UserStatistics.UserStatisticsBuilder statsBuilder, List<Transaction> transactions, User user) {
        Optional<LocalDate> firstDate = transactions.stream()
            .map(Transaction::getDate)
            .filter(Objects::nonNull)
            .min(LocalDate::compareTo);
            
        Optional<LocalDate> lastDate = transactions.stream()
            .map(Transaction::getDate)
            .filter(Objects::nonNull)
            .max(LocalDate::compareTo);
        
        Map<LocalDate, Long> dailyCounts = transactions.stream()
            .filter(t -> t.getDate() != null)
            .collect(Collectors.groupingBy(Transaction::getDate, Collectors.counting()));
            
        Optional<Map.Entry<LocalDate, Long>> mostActiveEntry = dailyCounts.entrySet().stream()
            .max(Map.Entry.comparingByValue());
        
        long accountAgeInDays = user.getCreatedAt() != null ? 
            ChronoUnit.DAYS.between(user.getCreatedAt().toLocalDate(), LocalDate.now()) : 0;
        double avgTransactionsPerDay = accountAgeInDays > 0 ? 
            (double) transactions.size() / accountAgeInDays : 0.0;
        
        statsBuilder
            .firstTransactionDate(firstDate.orElse(null))
            .mostRecentTransactionDate(lastDate.orElse(null))
            .mostActiveDay(mostActiveEntry.map(Map.Entry::getKey).orElse(null))
            .mostActiveDayCount(mostActiveEntry.map(Map.Entry::getValue).orElse(0L))
            .accountAgeInDays(accountAgeInDays)
            .averageTransactionsPerDay(avgTransactionsPerDay);
    }

    private void calculateRecentActivity(UserStatistics.UserStatisticsBuilder statsBuilder, List<Transaction> transactions) {
        Optional<Transaction> mostRecentTransaction = transactions.stream()
            .filter(t -> t.getCreatedAt() != null)
            .max(Comparator.comparing(Transaction::getCreatedAt));
        
        if (mostRecentTransaction.isPresent()) {
            Transaction recent = mostRecentTransaction.get();
            statsBuilder
                .lastTransactionDateTime(recent.getCreatedAt())
                .lastTransactionDescription(recent.getDescription())
                .lastTransactionAmount(recent.getAmount());
        }
    }

    private void calculateMonthlyTrends(UserStatistics.UserStatisticsBuilder statsBuilder, List<Transaction> transactions) {
        Map<String, Long> monthlyCounts = new LinkedHashMap<>();
        Map<String, Double> monthlyAmounts = new LinkedHashMap<>();
        
        LocalDate now = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM");
        
        for (int i = 11; i >= 0; i--) {
            YearMonth yearMonth = YearMonth.from(now.minusMonths(i));
            String monthKey = yearMonth.format(formatter);
            monthlyCounts.put(monthKey, 0L);
            monthlyAmounts.put(monthKey, 0.0);
        }
        
        Map<String, List<Transaction>> transactionsByMonth = transactions.stream()
            .filter(t -> t.getDate() != null)
            .filter(t -> !t.getDate().isBefore(now.minusMonths(12)))
            .collect(Collectors.groupingBy(t -> 
                YearMonth.from(t.getDate()).format(formatter)));
        
        transactionsByMonth.forEach((month, monthTransactions) -> {
            monthlyCounts.put(month, (long) monthTransactions.size());
            monthlyAmounts.put(month, monthTransactions.stream()
                .mapToDouble(Transaction::getAmount)
                .sum());
        });
        
        statsBuilder
            .monthlyTransactionCounts(monthlyCounts)
            .monthlyTransactionAmounts(monthlyAmounts);
    }
}
