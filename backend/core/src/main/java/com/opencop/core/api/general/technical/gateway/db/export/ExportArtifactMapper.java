// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.export;

import com.opencop.core.api.general.business.export.ExportArtifact;

final class ExportArtifactMapper {

  private ExportArtifactMapper() {}

  static ExportArtifact toModel(ExportArtifactEntity entity) {
    return new ExportArtifact(
        entity.getExportId(),
        entity.getSubjectType(),
        entity.getSubjectId(),
        entity.getCreatorId(),
        entity.getFormat(),
        entity.getBlobPath(),
        entity.getFilename(),
        entity.getGeneratedAt(),
        entity.getExpiresAt());
  }

  static ExportArtifactEntity toEntity(ExportArtifact artifact) {
    var entity = new ExportArtifactEntity();
    entity.setExportId(artifact.exportId());
    entity.setSubjectType(artifact.subjectType());
    entity.setSubjectId(artifact.subjectId());
    entity.setCreatorId(artifact.creatorId());
    entity.setFormat(artifact.format());
    entity.setBlobPath(artifact.blobPath());
    entity.setFilename(artifact.filename());
    entity.setGeneratedAt(artifact.generatedAt());
    entity.setExpiresAt(artifact.expiresAt());
    return entity;
  }
}
