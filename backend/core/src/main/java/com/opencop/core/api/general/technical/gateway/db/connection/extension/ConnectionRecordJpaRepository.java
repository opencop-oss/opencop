// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.connection.extension;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConnectionRecordJpaRepository
    extends JpaRepository<ConnectionRecordEntity, ConnectionRecordEntityId> {

  List<ConnectionRecordEntity>
      findBySourceIdAndIntegrationIdAndExtensionIdAndCollectionOrderByRecordKey(
          UUID sourceId, UUID integrationId, String extensionId, String collection);

  boolean existsBySourceId(UUID sourceId);

  long countBySourceIdAndIntegrationIdAndExtensionId(
      UUID sourceId, UUID integrationId, String extensionId);
}
