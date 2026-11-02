// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.iam.layer;

import java.util.Set;
import java.util.UUID;

public record UpdateLayerRoleRequest(
    Set<UUID> members, Set<UUID> editors, Set<UUID> admins, Set<UUID> owners) {}
