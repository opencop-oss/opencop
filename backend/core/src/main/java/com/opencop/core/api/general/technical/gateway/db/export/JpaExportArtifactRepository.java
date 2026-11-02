// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.export;

import com.opencop.core.api.general.business.export.ExportArtifact;
import com.opencop.core.api.general.ports.gateway.export.ExportArtifactRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaExportArtifactRepository implements ExportArtifactRepository {

  private final ExportArtifactJpaRepository repository;

  public JpaExportArtifactRepository(ExportArtifactJpaRepository repository) {
    this.repository = repository;
  }

  @Override
  @Transactional
  public void save(ExportArtifact artifact) {
    repository.save(ExportArtifactMapper.toEntity(artifact));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<ExportArtifact> findById(UUID exportId) {
    return repository.findById(exportId).map(ExportArtifactMapper::toModel);
  }

  @Override
  @Transactional(readOnly = true)
  public List<ExportArtifact> findExpiredBefore(OffsetDateTime expiresBefore) {
    return repository.findByExpiresAtBeforeOrderByExpiresAtAscExportIdAsc(expiresBefore).stream()
        .map(ExportArtifactMapper::toModel)
        .toList();
  }
}
