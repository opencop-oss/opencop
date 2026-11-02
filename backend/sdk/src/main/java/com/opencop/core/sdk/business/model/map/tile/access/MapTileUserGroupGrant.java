// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.map.tile.access;

import com.opencop.core.sdk.model.iam.group.GroupManagementSource;
import java.time.Instant;
import java.util.UUID;

/** One user group's grant on a tile, enriched with the group name for display. */
public record MapTileUserGroupGrant(
    UUID mapTileId,
    UUID userGroupId,
    Instant grantedAt,
    String name,
    GroupManagementSource source) {}
