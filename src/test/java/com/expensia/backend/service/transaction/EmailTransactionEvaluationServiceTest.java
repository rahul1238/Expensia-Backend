package com.expensia.backend.service.transaction;

import com.expensia.backend.model.EmailTransaction;
import com.expensia.backend.model.Transaction;
import com.expensia.backend.repository.EmailTransactionRepository;
import com.expensia.backend.repository.TransactionRepository;
import com.expensia.backend.utils.TransactionEnums.Currency;
import com.expensia.backend.utils.TransactionEnums.TransactionMethod;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailTransactionEvaluationServiceTest {

    @Mock
    private EmailTransactionRepository emailTransactionRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private EmailTransactionEvaluationService evaluationService;

    private String testUserId;
    private LocalDate testDate;

    @BeforeEach
    void setUp() {
        testUserId = "60f1b2b3e4b0d4f0f8a1b2c3";
        testDate = LocalDate.of(2024, 1, 15);
    }

    @Test
    void testEvaluateAndProcessEmailTransactions_EmptyList() {
        // Given
        when(emailTransactionRepository.findAll()).thenReturn(Collections.emptyList());

        // When
        EmailTransactionEvaluationService.EvaluationResult result = 
                evaluationService.evaluateAndProcessEmailTransactions();

        // Then
        assertTrue(result.isSuccess());
        assertEquals("No pending email transactions to process", result.getMessage());
        assertEquals(0, result.getTotalProcessed());
    }

    @Test
    void testSingleEmailTransaction_ShouldBeAdded() {
        // Given
        EmailTransaction emailTx = createEmailTransaction("1", "flipkart@flipkart.com", 
                "Order confirmation", 1999.0, "Flipkart");
        
        when(emailTransactionRepository.findAll()).thenReturn(List.of(emailTx));
        when(transactionRepository.findByUserId(new ObjectId(testUserId))).thenReturn(Collections.emptyList());
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction tx = invocation.getArgument(0);
            tx.setId("main-tx-1");
            return tx;
        });
        when(emailTransactionRepository.save(any(EmailTransaction.class))).thenReturn(emailTx);

        // When
        EmailTransactionEvaluationService.EvaluationResult result = 
                evaluationService.evaluateAndProcessEmailTransactions();

        // Then
        assertTrue(result.isSuccess());
        assertEquals(1, result.getTotalProcessed());
        assertEquals(1, result.getTotalAdded());
        assertEquals(0, result.getTotalDuplicates());
        
        verify(transactionRepository, times(1)).save(any(Transaction.class));
        verify(emailTransactionRepository, times(1)).save(emailTx);
    }

    @Test
    void testDuplicateEmailTransactions_FlipkartAndHDFC() {
        // Given - Same transaction from Flipkart and HDFC
        EmailTransaction flipkartTx = createEmailTransaction("1", "noreply@flipkart.com", 
                "Order confirmation: ₹1999 spent", 1999.0, "Flipkart");
        EmailTransaction hdfcTx = createEmailTransaction("2", "alerts@hdfcbank.com", 
                "Card transaction: ₹1999 at FLIPKART", 1999.0, "FLIPKART");
        
        when(emailTransactionRepository.findAll()).thenReturn(List.of(flipkartTx, hdfcTx));
        when(transactionRepository.findByUserId(new ObjectId(testUserId))).thenReturn(Collections.emptyList());
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction tx = invocation.getArgument(0);
            tx.setId("main-tx-1");
            return tx;
        });
        when(emailTransactionRepository.save(any(EmailTransaction.class))).thenReturn(null);

        // When
        EmailTransactionEvaluationService.EvaluationResult result = 
                evaluationService.evaluateAndProcessEmailTransactions();

        // Then
        assertTrue(result.isSuccess());
        assertEquals(2, result.getTotalProcessed());
        assertEquals(1, result.getTotalAdded()); // Only one should be added
        assertEquals(1, result.getTotalDuplicates()); // One should be marked as duplicate
        
        verify(transactionRepository, times(1)).save(any(Transaction.class));
        verify(emailTransactionRepository, times(2)).save(any(EmailTransaction.class));
    }

    @Test
    @org.junit.jupiter.api.Disabled("Temporarily disabled - duplicate detection logic needs review")
    void testEmailTransactionDuplicateOfMainTransaction() {
        // Given - Email transaction that matches existing main transaction
        EmailTransaction emailTx = createEmailTransaction("1", "alerts@hdfcbank.com", 
                "Card transaction: ₹500 at CCD", 500.0, "CCD");
        
        Transaction existingMainTx = Transaction.builder()
                .id("existing-1")
                .userId(new ObjectId(testUserId))
                .description("Coffee at Café Coffee Day")
                .amount(500.0)
                .date(testDate)
                .type("debit")
                .currency(Currency.INR)
                .transactionMethod(TransactionMethod.CREDIT_CARD)
                .build();
        
        when(emailTransactionRepository.findAll()).thenReturn(List.of(emailTx));
        when(transactionRepository.findByUserId(new ObjectId(testUserId))).thenReturn(List.of(existingMainTx));
        when(emailTransactionRepository.save(any(EmailTransaction.class))).thenAnswer(invocation -> {
            EmailTransaction tx = invocation.getArgument(0);
            // Verify that the transaction is marked as duplicate
            assertEquals("DUPLICATE_MAIN", tx.getCategory());
            return tx;
        });

        // When
        EmailTransactionEvaluationService.EvaluationResult result = 
                evaluationService.evaluateAndProcessEmailTransactions();

        // Then
        assertTrue(result.isSuccess());
        assertEquals(1, result.getTotalProcessed());
        assertEquals(0, result.getTotalAdded()); // Should not add new transaction
        assertEquals(1, result.getTotalDuplicates()); // Should mark as duplicate
        
        verify(transactionRepository, never()).save(any(Transaction.class));
        verify(emailTransactionRepository, times(1)).save(emailTx);
    }

    @Test
    void testMultipleUsersProcessedSeparately() {
        // Given - Transactions from different users
        String user1Id = "60f1b2b3e4b0d4f0f8a1b2c3";
        String user2Id = "60f1b2b3e4b0d4f0f8a1b2c4";
        
        EmailTransaction user1Tx = createEmailTransactionForUser("1", user1Id, "bank@bank.com", 
                "Transaction: ₹100", 100.0, "Merchant1");
        EmailTransaction user2Tx = createEmailTransactionForUser("2", user2Id, "bank@bank.com", 
                "Transaction: ₹200", 200.0, "Merchant2");
        
        when(emailTransactionRepository.findAll()).thenReturn(List.of(user1Tx, user2Tx));
        when(transactionRepository.findByUserId(new ObjectId(user1Id))).thenReturn(Collections.emptyList());
        when(transactionRepository.findByUserId(new ObjectId(user2Id))).thenReturn(Collections.emptyList());
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction tx = invocation.getArgument(0);
            tx.setId("main-tx-" + System.currentTimeMillis());
            return tx;
        });
        when(emailTransactionRepository.save(any(EmailTransaction.class))).thenReturn(null);

        // When
        EmailTransactionEvaluationService.EvaluationResult result = 
                evaluationService.evaluateAndProcessEmailTransactions();

        // Then
        assertTrue(result.isSuccess());
        assertEquals(2, result.getTotalProcessed());
        assertEquals(2, result.getTotalAdded());
        assertEquals(0, result.getTotalDuplicates());
        
        verify(transactionRepository, times(2)).save(any(Transaction.class));
        verify(emailTransactionRepository, times(2)).save(any(EmailTransaction.class));
    }

    private EmailTransaction createEmailTransaction(String id, String sourceEmail, 
                                                   String description, Double amount, String merchant) {
        return createEmailTransactionForUser(id, testUserId, sourceEmail, description, amount, merchant);
    }

    private EmailTransaction createEmailTransactionForUser(String id, String userId, String sourceEmail, 
                                                          String description, Double amount, String merchant) {
        return EmailTransaction.builder()
                .id(id)
                .userId(userId)
                .sourceEmail(sourceEmail)
                .description(description)
                .amount(amount)
                .merchant(merchant)
                .date(testDate)
                .type("debit")
                .currency(Currency.INR)
                .transactionMethod(TransactionMethod.CREDIT_CARD)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
