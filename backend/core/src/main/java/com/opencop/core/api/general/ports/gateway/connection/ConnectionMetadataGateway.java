// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.gateway.connection;

import com.opencop.core.api.general.business.model.connection.ConnectionMetadata;
import com.opencop.core.api.general.business.model.connection.ValidatedConnectionMetadataPatch;
import com.opencop.core.api.general.business.model.connection.access.ConnectionAccess;
import java.util.List;
import java.util.UUID;

public interface ConnectionMetadataGateway {

  ConnectionMetadata lockById(UUID sourceId);

  List<ConnectionMetadata> findNonArchivedByIntegrationId(UUID integrationId);

  void apply(UUID sourceId, ValidatedConnectionMetadataPatch patch, UUID updatedBy);

  /**
   * Replaces a connection's access mode and the mappers it reads and writes with, together, so the
   * stored access always holds exactly the mappers its mode needs. Joins the caller's transaction.
   */
  void applyAccess(UUID sourceId, ConnectionAccess access, UUID updatedBy);
}
