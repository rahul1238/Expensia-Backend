package com.expensia.backend.scheduler;

import com.expensia.backend.service.transaction.EmailTransactionEvaluationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailTransactionEvaluationScheduler {

    private final EmailTransactionEvaluationService evaluationService;

    /**
     * Runs email transaction evaluation every 15 minutes
     * This ensures that email transactions are processed and added to main transactions promptly
     */
    @Scheduled(fixedRate = 15 * 60 * 1000L, initialDelay = 60 * 1000L) // 15 minutes, 1 minute initial delay
    public void evaluateEmailTransactions() {
        try {
            log.info("Starting scheduled email transaction evaluation");
            
            EmailTransactionEvaluationService.EvaluationResult result = 
                    evaluationService.evaluateAndProcessEmailTransactions();
            
            if (result.isSuccess()) {
                log.info("Email transaction evaluation completed successfully: {}", result.getMessage());
                if (result.getTotalProcessed() > 0) {
                    log.info("Evaluation summary - Processed: {}, Added: {}, Duplicates: {}, Skipped: {}, Time: {}ms", 
                            result.getTotalProcessed(), 
                            result.getTotalAdded(), 
                            result.getTotalDuplicates(), 
                            result.getTotalSkipped(),
                            result.getProcessingTimeMs());
                }
            } else {
                log.error("Email transaction evaluation failed: {}", result.getError());
            }
            
        } catch (Exception e) {
            log.error("Unexpected error during scheduled email transaction evaluation", e);
        }
    }
    
    /**
     * Runs a more comprehensive evaluation daily at 2 AM
     * This helps catch any transactions that might have been missed in regular cycles
     */
    @Scheduled(cron = "0 0 2 * * ?") // Daily at 2 AM
    public void dailyEmailTransactionCleanup() {
        try {
            log.info("Starting daily email transaction cleanup and evaluation");
            
            EmailTransactionEvaluationService.EvaluationResult result = 
                    evaluationService.evaluateAndProcessEmailTransactions();
            
            if (result.isSuccess()) {
                log.info("Daily email transaction cleanup completed: {}", result.getMessage());
                log.info("Daily cleanup summary - Processed: {}, Added: {}, Duplicates: {}, Skipped: {}", 
                        result.getTotalProcessed(), 
                        result.getTotalAdded(), 
                        result.getTotalDuplicates(), 
                        result.getTotalSkipped());
            } else {
                log.error("Daily email transaction cleanup failed: {}", result.getError());
            }
            
        } catch (Exception e) {
            log.error("Unexpected error during daily email transaction cleanup", e);
        }
    }
}
