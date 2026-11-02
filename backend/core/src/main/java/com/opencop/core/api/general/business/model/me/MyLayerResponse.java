// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.me;

import com.opencop.core.sdk.technical.gateway.db.iam.LayerAccessScope;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import java.time.Instant;
import java.util.UUID;

/**
 * A layer as seen from one user's memberships: the user's effective role plus the layer's audit
 * metadata.
 *
 * <p>{@code lastEventAt} is null when no activity has been recorded. {@code createdBy} and {@code
 * updatedBy} are ids; resolving them to usernames is the caller's job and is meant for
 * single-record views only.
 */
public record MyLayerResponse(
    UUID layerId,
    String layerName,
    LayerRole role,
    LayerAccessScope accessScope,
    Instant createdAt,
    UUID createdBy,
    Instant updatedAt,
    UUID updatedBy,
    Instant lastEventAt,
    Instant archivedAt,
    UUID archivedBy,
    boolean defaultVisible) {}
