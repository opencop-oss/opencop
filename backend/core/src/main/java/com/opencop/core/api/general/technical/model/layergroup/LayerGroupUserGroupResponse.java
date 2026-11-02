// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.model.layergroup;

import com.opencop.core.sdk.model.iam.group.GroupManagementSource;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupRole;
import java.time.Instant;
import java.util.UUID;

public record LayerGroupUserGroupResponse(
    UUID userGroupId,
    String name,
    LayerGroupRole role,
    Instant assignedAt,
    GroupManagementSource source) {}
