// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.model.group;

import com.opencop.core.sdk.model.iam.group.GroupManagementSource;
import java.time.Instant;
import java.util.UUID;

public record GroupResponse(
    UUID id, String name, String description, Instant createdAt, GroupManagementSource source) {}
