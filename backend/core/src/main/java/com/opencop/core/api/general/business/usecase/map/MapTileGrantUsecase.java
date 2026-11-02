// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.map;

import com.opencop.core.api.general.business.policy.MapTilePolicy;
import com.opencop.core.api.general.technical.gateway.tile.access.MapTileGrantService;
import com.opencop.core.sdk.business.model.map.tile.access.MapTileMember;
import com.opencop.core.sdk.business.model.map.tile.access.MapTileUserGroupGrant;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Reading and changing who may see a tile.
 *
 * <p>Every operation here is global-admin only. Imagery is managed by global admins, so a grant
 * carries no role and holding one confers no ability to change the roster.
 */
@Service
public class MapTileGrantUsecase {

  private final TransactionTemplate transaction;
  private final MapTileGrantService grants;

  public MapTileGrantUsecase(TransactionTemplate transaction, MapTileGrantService grants) {
    this.transaction = transaction;
    this.grants = grants;
  }

  public List<MapTileMember> getMembers(UUID id, OpenCopPrincipal principal) {
    MapTilePolicy.assertMayManage(principal.isGlobalAdmin());
    return grants.listMembers(id);
  }

  public List<MapTileUserGroupGrant> getUserGroups(UUID id, OpenCopPrincipal principal) {
    MapTilePolicy.assertMayManage(principal.isGlobalAdmin());
    return grants.listUserGroups(id);
  }

  public void assignMembers(UUID id, Collection<UUID> userIds, OpenCopPrincipal principal) {
    MapTilePolicy.assertMayManage(principal.isGlobalAdmin());
    inTransaction(() -> grants.assignMembers(id, userIds));
  }

  public void revokeMembers(UUID id, Collection<UUID> userIds, OpenCopPrincipal principal) {
    MapTilePolicy.assertMayManage(principal.isGlobalAdmin());
    inTransaction(() -> grants.revokeMembers(id, userIds));
  }

  public void assignUserGroups(UUID id, Collection<UUID> groupIds, OpenCopPrincipal principal) {
    MapTilePolicy.assertMayManage(principal.isGlobalAdmin());
    inTransaction(() -> grants.assignUserGroups(id, groupIds));
  }

  public void revokeUserGroups(UUID id, Collection<UUID> groupIds, OpenCopPrincipal principal) {
    MapTilePolicy.assertMayManage(principal.isGlobalAdmin());
    inTransaction(() -> grants.revokeUserGroups(id, groupIds));
  }

  /** Runs the mutation as one atomic batch, so a partly-applied change cannot be committed. */
  private void inTransaction(Runnable work) {
    transaction.execute(
        _ -> {
          work.run();
          return null;
        });
  }
}
