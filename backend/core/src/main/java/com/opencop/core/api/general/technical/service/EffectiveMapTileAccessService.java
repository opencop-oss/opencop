// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.service;

import com.opencop.core.api.general.technical.gateway.tile.MapTileEntity;
import com.opencop.core.api.general.technical.gateway.tile.MapTileRepository;
import com.opencop.core.api.general.technical.gateway.tile.access.MapTileMemberRepository;
import com.opencop.core.api.general.technical.gateway.tile.access.MapTileUserGroupRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.MapTileAccessScope;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves which imagery tiles a user can reach, by any path: a direct grant, a grant to a user
 * group they belong to, or the tile being GLOBAL.
 *
 * <p>Global admins are handled by callers, which short-circuit before asking. Enablement is a
 * separate axis applied by the caller, so an admin can still resolve access on a disabled tile.
 */
@Service
public class EffectiveMapTileAccessService {

  private final MapTileRepository mapTileRepository;
  private final MapTileMemberRepository memberRepository;
  private final MapTileUserGroupRepository userGroupRepository;

  public EffectiveMapTileAccessService(
      MapTileRepository mapTileRepository,
      MapTileMemberRepository memberRepository,
      MapTileUserGroupRepository userGroupRepository) {
    this.mapTileRepository = mapTileRepository;
    this.memberRepository = memberRepository;
    this.userGroupRepository = userGroupRepository;
  }

  /** Whether any path reaches {@code userId} on {@code mapTileId}. */
  @Transactional(readOnly = true)
  public boolean canReach(UUID mapTileId, UUID userId) {
    return mapTileRepository.findById(mapTileId).map(tile -> canReach(tile, userId)).orElse(false);
  }

  /**
   * As {@link #canReach(UUID, UUID)}, for a tile the caller has already loaded. Callers that need
   * another field off the same row take this overload so the row is read once rather than twice.
   */
  @Transactional(readOnly = true)
  public boolean canReach(MapTileEntity tile, UUID userId) {
    if (tile.getAccessScope() == MapTileAccessScope.GLOBAL) {
      return true;
    }
    var mapTileId = tile.getId();
    return memberRepository.findByIdMapTileIdAndIdUserId(mapTileId, userId).isPresent()
        || !userGroupRepository.findAllReachableByUserIdAndMapTileId(userId, mapTileId).isEmpty();
  }

  /**
   * Every tile {@code userId} can reach. One pass per grant table rather than a lookup per tile, so
   * the query count does not grow with the catalog.
   */
  @Transactional(readOnly = true)
  public Set<UUID> reachableTileIds(UUID userId) {
    Set<UUID> reachable = new HashSet<>();

    for (MapTileEntity tile : mapTileRepository.findAll()) {
      if (tile.getAccessScope() == MapTileAccessScope.GLOBAL) {
        reachable.add(tile.getId());
      }
    }
    for (var grant : userGroupRepository.findAllReachableByUserId(userId)) {
      reachable.add(grant.getMapTileId());
    }
    for (var grant : memberRepository.findAllByIdUserId(userId)) {
      reachable.add(grant.getMapTileId());
    }

    return reachable;
  }
}
