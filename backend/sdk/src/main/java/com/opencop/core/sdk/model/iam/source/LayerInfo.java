// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.iam.source;

import com.opencop.core.sdk.technical.gateway.db.iam.LayerAccessScope;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import java.time.Instant;
import java.util.UUID;

public record LayerInfo(
    UUID layerId,
    String layerName,
    LayerRole role,
    LayerAccessScope accessScope,
    Instant createdAt,
    boolean defaultVisible) {}
