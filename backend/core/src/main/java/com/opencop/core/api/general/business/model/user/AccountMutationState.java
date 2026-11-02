// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.user;

import java.time.Instant;
import java.util.UUID;

public record AccountMutationState(
    UUID internalUserId,
    String email,
    boolean active,
    boolean administrator,
    long sessionGeneration,
    long accountRevision,
    Instant deletedAt) {}
