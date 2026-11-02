// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.ports.gateway.repository;

import com.opencop.api.connect.business.connector.DesiredSocketSource;
import com.opencop.core.sdk.model.datasource.SocketDataSource;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Read-only view of Core-owned socket source desired ownership. */
public interface DesiredSocketSourceRepository {

  default String diagnosticLabel() {
    return "desired-state repository";
  }

  /** Reads one source with its stored integration settings overrides, to run it. */
  Optional<DesiredSocketSource> findSocketSourceById(UUID sourceId);

  /** Reads every source to run here, each with its stored integration settings overrides. */
  List<DesiredSocketSource> findEnabledSocketSourcesAssignedTo(String instanceId);

  /** Reads the desired rows only, to decide whether local runtimes are still desired here. */
  Map<UUID, SocketDataSource> findSocketSourcesByIds(Set<UUID> sourceIds);
}
