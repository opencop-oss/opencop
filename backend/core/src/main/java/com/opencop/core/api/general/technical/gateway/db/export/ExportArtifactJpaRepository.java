// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.export;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExportArtifactJpaRepository extends JpaRepository<ExportArtifactEntity, UUID> {

  List<ExportArtifactEntity> findByExpiresAtBeforeOrderByExpiresAtAscExportIdAsc(
      OffsetDateTime expiresBefore);
}
