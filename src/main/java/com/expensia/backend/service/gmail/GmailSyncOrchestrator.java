package com.expensia.backend.service.gmail;

import com.expensia.backend.model.GmailCredential;
import com.expensia.backend.repository.GmailCredentialRepository;
import com.expensia.backend.repository.TokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class GmailSyncOrchestrator {

    private final GmailCredentialRepository credentialRepository;
    private final TokenRepository tokenRepository;
    private final EnhancedGmailSyncService enhancedGmailSyncService;

    /**
     * startServerSync: Runs on server start for all valid logged-in users.
     */
    public void startServerSync() {
        // Fetch all users having Gmail creds
        List<GmailCredential> creds = credentialRepository.findAll();
        if (creds.isEmpty()) return;

        // Determine currently logged-in users (non-revoked tokens)
        Set<String> loggedInUserIds = new HashSet<>();
        for (GmailCredential c : creds) {
            if (c.getUserId() == null) continue;
            var list = tokenRepository.findAllByUserIdAndRevokedFalse(new ObjectId(c.getUserId()));
            if (!list.isEmpty()) loggedInUserIds.add(c.getUserId());
        }

        // Trigger sync for valid and logged-in users only
        for (GmailCredential c : creds) {
            if (c.getUserId() == null || c.getUserId().isBlank()) continue;
            if (c.getRefreshToken() == null || c.getRefreshToken().isBlank()) continue;
            if (!loggedInUserIds.contains(c.getUserId())) continue;

            try {
                // Fire asynchronously to avoid blocking startup
                syncUserEmailsAsync(c.getUserId());
            } catch (Exception e) {
                log.warn("Startup sync dispatch failed for user {}: {}", c.getUserId(), e.getMessage());
            }
        }
    }

    /**
     * syncUserEmails: Triggered for a single user on demand.
     */
    public EnhancedGmailSyncService.SyncResult syncUserEmails(String userId) {
        try {
            // Ensure user is currently logged in
            var active = tokenRepository.findAllByUserIdAndRevokedFalse(new ObjectId(userId));
            if (active.isEmpty()) {
                return EnhancedGmailSyncService.SyncResult.builder()
                        .success(false)
                        .error("User not logged in")
                        .build();
            }
            return enhancedGmailSyncService.syncForUser(userId);
        } catch (Exception e) {
            log.error("Sync failed for user {}: {}", userId, e.getMessage(), e);
            return EnhancedGmailSyncService.SyncResult.builder().success(false).error(e.getMessage()).build();
        }
    }

    @Async("gmailSyncExecutor")
    public CompletableFuture<EnhancedGmailSyncService.SyncResult> syncUserEmailsAsync(String userId) {
        var result = syncUserEmails(userId);
        return CompletableFuture.completedFuture(result);
    }
}
