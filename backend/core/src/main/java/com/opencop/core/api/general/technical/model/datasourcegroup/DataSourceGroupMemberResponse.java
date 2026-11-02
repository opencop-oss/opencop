// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.model.datasourcegroup;

import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.DataSourceGroupRole;
import java.time.Instant;
import java.util.UUID;

public record DataSourceGroupMemberResponse(
    UUID userId,
    String username,
    String firstName,
    String lastName,
    String email,
    DataSourceGroupRole role,
    Instant joinedAt) {}
