package com.expensia.backend.controller;

import com.expensia.backend.service.transaction.EmailTransactionEvaluationOrchestrator;
import com.expensia.backend.service.transaction.EmailTransactionEvaluationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/email-transactions")
@RequiredArgsConstructor
@Slf4j
public class EmailTransactionEvaluationController {

    private final EmailTransactionEvaluationOrchestrator orchestrator;

    /**
     * Manually trigger email transaction evaluation and processing
     * This is synchronous and will wait for the evaluation to complete
     */
    @PostMapping("/evaluate")
    public ResponseEntity<?> triggerEvaluation() {
        try {
            log.info("Manual email transaction evaluation triggered via API");
            
            EmailTransactionEvaluationService.EvaluationResult result = orchestrator.triggerEvaluation();
            
            if (result.isSuccess()) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", result.getMessage(),
                        "processed", result.getTotalProcessed(),
                        "added", result.getTotalAdded(),
                        "duplicates", result.getTotalDuplicates(),
                        "skipped", result.getTotalSkipped(),
                        "processingTimeMs", result.getProcessingTimeMs(),
                        "details", result.getDetails()
                ));
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", result.getError()
                ));
            }
            
        } catch (Exception e) {
            log.error("Error triggering email transaction evaluation", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", "Failed to trigger evaluation: " + e.getMessage()
            ));
        }
    }

    /**
     * Asynchronously trigger email transaction evaluation
     * Returns immediately with a success message, evaluation happens in background
     */
    @PostMapping("/evaluate-async")
    public ResponseEntity<?> triggerEvaluationAsync() {
        try {
            log.info("Async email transaction evaluation triggered via API");
            
            orchestrator.triggerEvaluationAsync();
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Email transaction evaluation started in background",
                    "timestamp", System.currentTimeMillis()
            ));
            
        } catch (Exception e) {
            log.error("Error triggering async email transaction evaluation", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", "Failed to trigger async evaluation: " + e.getMessage()
            ));
        }
    }

    /**
     * Get the current status of email transaction processing
     * This provides insights into how many email transactions are pending processing
     */
    @GetMapping("/status")
    public ResponseEntity<?> getEvaluationStatus() {
        try {
            // This would need additional implementation in the service to get current status
            // For now, return a simple status
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Email transaction evaluation service is running",
                    "lastCheck", System.currentTimeMillis()
            ));
            
        } catch (Exception e) {
            log.error("Error getting evaluation status", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", "Failed to get status: " + e.getMessage()
            ));
        }
    }

    /**
     * Trigger evaluation for a specific user (admin function)
     */
    @PostMapping("/evaluate/{userId}")
    public ResponseEntity<?> triggerEvaluationForUser(@PathVariable String userId) {
        try {
            log.info("Manual email transaction evaluation triggered for user: {}", userId);
            
            EmailTransactionEvaluationService.EvaluationResult result = 
                    orchestrator.triggerEvaluationForUser(userId);
            
            if (result.isSuccess()) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", result.getMessage(),
                        "userId", userId,
                        "processed", result.getTotalProcessed(),
                        "added", result.getTotalAdded(),
                        "duplicates", result.getTotalDuplicates(),
                        "skipped", result.getTotalSkipped(),
                        "processingTimeMs", result.getProcessingTimeMs()
                ));
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", result.getError(),
                        "userId", userId
                ));
            }
            
        } catch (Exception e) {
            log.error("Error triggering email transaction evaluation for user: {}", userId, e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", "Failed to trigger evaluation for user: " + e.getMessage(),
                    "userId", userId
            ));
        }
    }
}
