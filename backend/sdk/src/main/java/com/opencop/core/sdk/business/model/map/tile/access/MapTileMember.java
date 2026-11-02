// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.map.tile.access;

import java.time.Instant;
import java.util.UUID;

/**
 * One user's grant on a tile, enriched with the identity fields the admin drawer's assigned-members
 * table renders. The grant carries no role: holding one means the user can see the tile.
 *
 * <p>Identity comes from the local {@code opencop_user} row rather than the IdP, so listing members
 * is a single query. A grant can outlive its user row (the join table carries no foreign key), in
 * which case the identity fields are null and only {@code userId} is meaningful.
 */
public record MapTileMember(
    UUID mapTileId,
    UUID userId,
    Instant grantedAt,
    String username,
    String firstName,
    String lastName,
    String email) {}
