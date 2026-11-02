// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.tile.access;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface MapTileMemberRepository
    extends JpaRepository<MapTileMemberEntity, MapTileMemberIdEntity> {

  List<MapTileMemberEntity> findAllByIdMapTileId(UUID mapTileId);

  /**
   * Members of one tile with their identity fields, in one query.
   *
   * <p>LEFT JOIN because the table has no foreign key on {@code user_id}. A grant may name a user
   * who is not provisioned locally, and an inner join would hide that grant.
   */
  @Query(
      value =
          "select m.map_tile_id, m.user_id, m.granted_at,"
              + " u.username, u.first_name, u.last_name, u.email"
              + " from iam.map_tile_member m"
              + " left join opencop_user u on u.id = m.user_id"
              + " where m.map_tile_id = :mapTileId"
              + " order by u.username nulls last",
      nativeQuery = true)
  List<MapTileMemberRow> findMembersWithIdentity(UUID mapTileId);

  /** Projection for {@link #findMembersWithIdentity}. */
  interface MapTileMemberRow {
    UUID getMapTileId();

    UUID getUserId();

    Instant getGrantedAt();

    String getUsername();

    String getFirstName();

    String getLastName();

    String getEmail();
  }

  Optional<MapTileMemberEntity> findByIdMapTileIdAndIdUserId(UUID mapTileId, UUID userId);

  List<MapTileMemberEntity> findAllByIdUserId(UUID userId);

  @Modifying
  @Query(
      "delete from MapTileMemberEntity m where m.id.mapTileId = :mapTileId"
          + " and m.id.userId in :userIds")
  int deleteAllByMapTileIdAndUserIds(UUID mapTileId, List<UUID> userIds);
}
