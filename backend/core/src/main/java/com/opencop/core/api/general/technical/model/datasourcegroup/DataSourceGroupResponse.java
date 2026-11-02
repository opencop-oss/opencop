// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.model.datasourcegroup;

import java.time.Instant;
import java.util.UUID;

public record DataSourceGroupResponse(
    UUID id, String name, String description, Instant createdAt) {}
