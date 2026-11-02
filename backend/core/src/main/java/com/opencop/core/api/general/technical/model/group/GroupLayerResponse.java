// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.model.group;

import com.opencop.core.sdk.model.iam.group.GroupManagementSource;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import java.time.Instant;
import java.util.UUID;

public record GroupLayerResponse(
    UUID groupId,
    String groupName,
    GroupManagementSource groupSource,
    UUID layerId,
    String layerName,
    LayerRole role,
    Instant assignedAt) {}
