// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.user;

import java.time.Instant;
import java.util.UUID;

public record InternalUser(
    UUID id,
    String idpUserId,
    String idpUsername,
    String issuer,
    String username,
    String email,
    String firstName,
    String lastName,
    String displayName,
    boolean active,
    boolean admin,
    long sessionGeneration,
    long accountAccessRevision,
    Instant createdAt,
    Instant updatedAt,
    Instant deletedAt) {}
