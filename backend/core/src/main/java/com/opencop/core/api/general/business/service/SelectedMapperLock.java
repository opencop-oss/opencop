// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.service;

import com.opencop.core.api.general.business.model.connection.access.ConnectionAccess;
import com.opencop.core.api.general.business.policy.connection.access.SelectedMapperPolicy;
import com.opencop.core.api.general.ports.gateway.profile.MapperCatalogGateway;
import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Keeps a new connection's mappers from being archived between its validation and its commit, for
 * every transport. Called inside the creation's transaction, after its integration is locked: the
 * mappers stay locked until the connection is stored, and one archived meanwhile refuses the
 * creation.
 */
@Component
public class SelectedMapperLock {

  private final MapperCatalogGateway locks;

  public SelectedMapperLock(MapperCatalogGateway locks) {
    this.locks = locks;
  }

  /** Locks the mappers the access reads and writes with, refusing any one archived. */
  public void hold(ConnectionAccess access) {
    var mapperIds = new ArrayList<UUID>();
    access.ingressMapperId().ifPresent(mapperIds::add);
    access.egressMapperId().ifPresent(mapperIds::add);
    hold(mapperIds);
  }

  /** Locks the selected mappers, refusing any one archived. */
  public void hold(Collection<UUID> mapperIds) {
    SelectedMapperPolicy.assertNoneArchived(locks.lockAndFindArchived(mapperIds));
  }
}
