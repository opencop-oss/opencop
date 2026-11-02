// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.tile.access;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface MapTileUserGroupRepository
    extends JpaRepository<MapTileUserGroupEntity, MapTileUserGroupIdEntity> {

  List<MapTileUserGroupEntity> findAllByIdMapTileId(UUID mapTileId);

  /** Group grants on one tile with the group name, in one query. */
  @Query(
      value =
          "select g.map_tile_id, g.user_group_id, g.granted_at, ug.name, ug.idp_issuer"
              + " from iam.map_tile_user_group g"
              + " join iam.user_group ug on ug.id = g.user_group_id"
              + " where g.map_tile_id = :mapTileId"
              + " order by ug.name",
      nativeQuery = true)
  List<MapTileUserGroupRow> findGroupsWithName(UUID mapTileId);

  /** Projection for {@link #findGroupsWithName}. */
  interface MapTileUserGroupRow {
    UUID getMapTileId();

    UUID getUserGroupId();

    Instant getGrantedAt();

    String getName();

    /** Null for a locally-created group; classified into a source rather than exposed as-is. */
    String getIdpIssuer();
  }

  /**
   * Every group grant that reaches {@code userId} through their group memberships.
   *
   * <p>Native so the join needs no JPA mapping. Mapping {@code user_group_member} would pull {@code
   * GroupLayerMemberEntity} and the whole layer graph into this persistence unit.
   */
  @Query(
      value =
          "select g.* from iam.map_tile_user_group g"
              + " join iam.user_group_member m on m.group_id = g.user_group_id"
              + " where m.user_id = :userId",
      nativeQuery = true)
  List<MapTileUserGroupEntity> findAllReachableByUserId(UUID userId);

  /** As {@link #findAllReachableByUserId}, narrowed to one tile. */
  @Query(
      value =
          "select g.* from iam.map_tile_user_group g"
              + " join iam.user_group_member m on m.group_id = g.user_group_id"
              + " where m.user_id = :userId and g.map_tile_id = :mapTileId",
      nativeQuery = true)
  List<MapTileUserGroupEntity> findAllReachableByUserIdAndMapTileId(UUID userId, UUID mapTileId);

  @Modifying
  @Query(
      "delete from MapTileUserGroupEntity g where g.id.mapTileId = :mapTileId"
          + " and g.id.userGroupId in :userGroupIds")
  int deleteAllByMapTileIdAndUserGroupIds(UUID mapTileId, Collection<UUID> userGroupIds);
}
