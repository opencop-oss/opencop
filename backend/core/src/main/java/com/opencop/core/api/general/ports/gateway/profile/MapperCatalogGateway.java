// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.gateway.profile;

import com.opencop.core.api.general.business.model.profile.MapperCatalogProfile;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface MapperCatalogGateway {

  List<MapperCatalogProfile> loadCatalog();

  int updateMetadata(UUID mapperId, String name, String description);

  int reconcileAvailability(UUID mapperId, boolean archived);

  /**
   * Locks each mapper's identity until the caller's transaction ends, in identifier order, then
   * reports the mappers whose latest revision is archived. Holds the mappers a new connection
   * selected steady until the connection is stored.
   *
   * <p>The lock is shared, so concurrent creations selecting the same mapper do not wait on one
   * another, while an archive of that mapper waits for them. The report is read after every lock is
   * granted, so it sees an archive that committed while this waited.
   *
   * @return the selected mappers that are archived, empty when every one is usable
   */
  Set<UUID> lockAndFindArchived(Collection<UUID> mapperIds);

  /** Records the integration that owns the mapper. */
  int assignIntegration(UUID mapperId, UUID integrationId);
}
