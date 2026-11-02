// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.iam.source;

import java.util.Set;
import java.util.UUID;

public record UpdateSourcePermissionsRequest(
    Set<UUID> viewers, Set<UUID> admins, Set<UUID> owners) {}
