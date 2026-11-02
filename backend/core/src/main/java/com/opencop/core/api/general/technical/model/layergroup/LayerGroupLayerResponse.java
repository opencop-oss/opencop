// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.model.layergroup;

import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import java.time.Instant;
import java.util.UUID;

public record LayerGroupLayerResponse(
    UUID layerGroupId,
    String layerGroupName,
    UUID layerId,
    String layerName,
    LayerRole role,
    Instant assignedAt) {}
