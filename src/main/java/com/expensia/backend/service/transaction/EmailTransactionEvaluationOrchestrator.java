package com.expensia.backend.service.transaction;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailTransactionEvaluationOrchestrator {

    private final EmailTransactionEvaluationService evaluationService;

    /**
     * Triggers email transaction evaluation synchronously
     * 
     * @return EvaluationResult containing the results of the evaluation
     */
    public EmailTransactionEvaluationService.EvaluationResult triggerEvaluation() {
        log.info("Manual email transaction evaluation triggered");
        return evaluationService.evaluateAndProcessEmailTransactions();
    }

    /**
     * Triggers email transaction evaluation asynchronously
     * Useful for API endpoints that don't want to wait for the process to complete
     * 
     * @return CompletableFuture with the EvaluationResult
     */
    @Async("emailEvaluationExecutor")
    public CompletableFuture<EmailTransactionEvaluationService.EvaluationResult> triggerEvaluationAsync() {
        log.info("Async email transaction evaluation triggered");
        EmailTransactionEvaluationService.EvaluationResult result = evaluationService.evaluateAndProcessEmailTransactions();
        return CompletableFuture.completedFuture(result);
    }

    /**
     * Triggers evaluation for a specific user
     * 
     * @param userId The user ID to process email transactions for
     * @return EvaluationResult containing the results
     */
    public EmailTransactionEvaluationService.EvaluationResult triggerEvaluationForUser(String userId) {
        log.info("Manual email transaction evaluation triggered for user: {}", userId);
        return evaluationService.evaluateAndProcessEmailTransactions(userId);
    }
}
