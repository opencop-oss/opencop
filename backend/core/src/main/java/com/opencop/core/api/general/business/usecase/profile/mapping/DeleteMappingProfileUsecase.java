// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.profile.mapping;

import com.opencop.core.api.general.business.model.Caller;
import com.opencop.core.api.general.business.model.audit.AuditEntry.EntityType;
import com.opencop.core.api.general.business.model.audit.AuditEntry.Operation;
import com.opencop.core.api.general.business.model.audit.AuditEvent;
import com.opencop.core.api.general.business.policy.MapperQualifierPolicy;
import com.opencop.core.api.general.ports.gateway.audit.AuditRecorder;
import com.opencop.core.api.general.ports.usecase.profile.mapping.DeleteMappingProfile;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.ports.gateway.mapper.MapperProfileService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

public class DeleteMappingProfileUsecase implements DeleteMappingProfile {

  private final MapperProfileService service;
  private final AuditRecorder auditRecorder;
  private final MapperAuditProjection projection;

  public DeleteMappingProfileUsecase(
      final MapperProfileService service,
      final AuditRecorder auditRecorder,
      final MapperAuditProjection projection) {
    this.service = service;
    this.auditRecorder = auditRecorder;
    this.projection = projection;
  }

  /**
   * Archives the mapper in one transaction, which waits for any connection being created with it
   * and then refuses, since that connection now uses the mapper.
   */
  @Override
  @Transactional
  public void archive(UUID mapperId, Caller archivedBy) {
    if (!this.service.mapperProfileExists(mapperId)) {
      throw new GlobalOpenCopException("Mapper not found", HttpStatus.NOT_FOUND);
    }

    // Locked + uncached: the guards below decide whether to archive, so reading them from a cache
    // that may lag the database would let an already-archived or system mapper slip through.
    var profile = this.service.lockAndGetLatestMapperProfileMeta(mapperId);
    if (!MapperQualifierPolicy.deletable(profile)) {
      throw new GlobalOpenCopException(
          "An egress mapper belongs to its integration and cannot be deleted",
          HttpStatus.BAD_REQUEST);
    }
    if (Boolean.TRUE.equals(profile.archived())) {
      throw new GlobalOpenCopException("Mapper is already archived", HttpStatus.BAD_REQUEST);
    }

    if (Boolean.FALSE.equals(profile.editable())) {
      throw new GlobalOpenCopException("Cannot archive a system mapper", HttpStatus.BAD_REQUEST);
    }

    int rows = this.service.archiveMapperProfile(mapperId, archivedBy.userId());
    if (rows == 0) {
      throw new GlobalOpenCopException(
          "Cannot archive mapper: it is linked to one or more connections", HttpStatus.CONFLICT);
    }

    this.auditRecorder.record(
        new AuditEvent(
            EntityType.MAPPER_PROFILE,
            mapperId,
            profile.name(),
            Operation.ARCHIVE,
            archivedBy,
            this.projection.archive(profile),
            AuditEvent.CURRENT_CHANGE_VERSION));
  }
}
