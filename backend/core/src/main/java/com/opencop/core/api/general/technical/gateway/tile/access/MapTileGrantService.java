// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.tile.access;

import com.opencop.core.sdk.business.model.map.tile.access.MapTileMember;
import com.opencop.core.sdk.business.model.map.tile.access.MapTileUserGroupGrant;
import com.opencop.core.sdk.model.iam.group.GroupManagementSource;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads and mutates the two tile grant tables. A row means the principal can see the tile. */
@Service
public class MapTileGrantService {

  private final MapTileMemberRepository memberRepository;
  private final MapTileUserGroupRepository userGroupRepository;

  public MapTileGrantService(
      MapTileMemberRepository memberRepository, MapTileUserGroupRepository userGroupRepository) {
    this.memberRepository = memberRepository;
    this.userGroupRepository = userGroupRepository;
  }

  @Transactional(readOnly = true)
  public List<MapTileMember> listMembers(UUID mapTileId) {
    return memberRepository.findMembersWithIdentity(mapTileId).stream()
        .map(
            row ->
                new MapTileMember(
                    row.getMapTileId(),
                    row.getUserId(),
                    row.getGrantedAt(),
                    row.getUsername(),
                    row.getFirstName(),
                    row.getLastName(),
                    row.getEmail()))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<MapTileUserGroupGrant> listUserGroups(UUID mapTileId) {
    return userGroupRepository.findGroupsWithName(mapTileId).stream()
        .map(
            row ->
                new MapTileUserGroupGrant(
                    row.getMapTileId(),
                    row.getUserGroupId(),
                    row.getGrantedAt(),
                    row.getName(),
                    GroupManagementSource.ofIdpIssuer(row.getIdpIssuer())))
        .toList();
  }

  /** Grants each user access. Re-granting an existing member is a no-op. */
  @Transactional
  public void assignMembers(UUID mapTileId, Collection<UUID> userIds) {
    userIds.forEach(userId -> memberRepository.save(new MapTileMemberEntity(mapTileId, userId)));
  }

  @Transactional
  public void revokeMembers(UUID mapTileId, Collection<UUID> userIds) {
    memberRepository.deleteAllByMapTileIdAndUserIds(mapTileId, List.copyOf(userIds));
  }

  @Transactional
  public void assignUserGroups(UUID mapTileId, Collection<UUID> userGroupIds) {
    userGroupIds.forEach(
        userGroupId ->
            userGroupRepository.save(new MapTileUserGroupEntity(mapTileId, userGroupId)));
  }

  @Transactional
  public void revokeUserGroups(UUID mapTileId, Collection<UUID> userGroupIds) {
    userGroupRepository.deleteAllByMapTileIdAndUserGroupIds(mapTileId, userGroupIds);
  }
}
