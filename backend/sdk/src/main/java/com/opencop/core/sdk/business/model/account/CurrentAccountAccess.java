// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.account;

import java.util.UUID;

public record CurrentAccountAccess(
    UUID internalUserId,
    boolean active,
    boolean administrator,
    long sessionGeneration,
    long accountRevision) {}
