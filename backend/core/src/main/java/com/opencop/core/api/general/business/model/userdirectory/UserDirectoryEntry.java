// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.userdirectory;

import com.opencop.core.sdk.model.iam.userdirectory.UserDirectoryEntryType;
import java.time.Instant;
import java.util.UUID;

/**
 * A single row in the combined user/group directory grid. User-only attributes ({@code firstName},
 * {@code lastName}, {@code email}, {@code displayName}, {@code admin}) are null for group entries.
 */
public record UserDirectoryEntry(
    UUID id,
    UserDirectoryEntryType type,
    String name,
    String firstName,
    String lastName,
    String email,
    String displayName,
    Boolean admin,
    boolean active,
    Instant createdAt,
    Instant updatedAt) {}
