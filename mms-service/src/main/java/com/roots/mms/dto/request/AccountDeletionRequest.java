package com.roots.mms.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request to schedule self-service account deletion. The password re-auth is
 * optional for federated accounts (which have no local password) but required
 * for local accounts — enforced in {@code UserService.requestDeletion}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountDeletionRequest {
    private String currentPassword;
}
