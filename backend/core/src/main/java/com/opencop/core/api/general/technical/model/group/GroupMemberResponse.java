// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.model.group;

import java.time.Instant;
import java.util.UUID;

public record GroupMemberResponse(
    UUID userId,
    String username,
    String firstName,
    String lastName,
    String email,
    Instant joinedAt) {}
