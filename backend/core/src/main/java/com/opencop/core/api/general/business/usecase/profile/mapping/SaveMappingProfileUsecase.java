// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.profile.mapping;

import com.opencop.core.api.general.business.model.Caller;
import com.opencop.core.api.general.business.model.audit.AuditEntry.EntityType;
import com.opencop.core.api.general.business.model.audit.AuditEntry.Operation;
import com.opencop.core.api.general.business.model.audit.AuditEvent;
import com.opencop.core.api.general.ports.gateway.audit.AuditRecorder;
import com.opencop.core.api.general.ports.usecase.profile.mapping.SaveMappingProfile;
import com.opencop.core.sdk.business.model.profile.MapperProfileFullMeta;
import com.opencop.core.sdk.business.model.profile.MapperQualifier;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.ports.gateway.mapper.MapperProfileService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

public class SaveMappingProfileUsecase implements SaveMappingProfile {

  private static final int INITIAL_MAPPER_VERSION = 1;

  private final MapperProfileService service;
  private final AuditRecorder auditRecorder;
  private final MapperAuditProjection projection;

  public SaveMappingProfileUsecase(
      final MapperProfileService service,
      final AuditRecorder auditRecorder,
      final MapperAuditProjection projection) {
    this.service = service;
    this.auditRecorder = auditRecorder;
    this.projection = projection;
  }

  @Override
  @Transactional
  public UUID create(MapperProfileFullMeta meta, Caller createdBy) {
    if (this.service.mapperProfileExistsByName(meta.profile().name())) {
      throw new GlobalOpenCopException("Mapper name already taken", HttpStatus.CONFLICT);
    }
    // A mapper authored as data holds decoding rules, so it is always an ingress mapper, whatever
    // the request says.
    var mapper =
        meta.profile().toBuilder()
            .withQualifier(MapperQualifier.INGRESS)
            .withVersion(INITIAL_MAPPER_VERSION)
            .build();
    var id =
        this.service.createMapperProfile(
            mapper, meta.payload(), meta.filename(), createdBy.userId());

    this.auditRecorder.record(
        new AuditEvent(
            EntityType.MAPPER_PROFILE,
            id,
            mapper.name(),
            Operation.CREATE,
            createdBy,
            this.projection.create(mapper),
            AuditEvent.CURRENT_CHANGE_VERSION));

    return id;
  }

  @Override
  @Transactional
  public void update(MapperProfileFullMeta meta, Caller updatedBy) {
    // Locked + uncached: the version guard below is only sound when no concurrent save can slip in
    // and when the read isn't served from the mapper cache.
    var dbProfile = this.service.lockAndGetLatestMapperProfileMeta(meta.profile().id());
    if (dbProfile == null) {
      throw new GlobalOpenCopException("Mapper does not exist", HttpStatus.NOT_FOUND);
    }

    if (Boolean.TRUE.equals(dbProfile.archived())) {
      throw new GlobalOpenCopException("Cannot update an archived mapper", HttpStatus.BAD_REQUEST);
    }

    // Optimistic concurrency guard — null version skips the check for back-compat.
    Integer expected = meta.profile().version();
    if (expected != null && !expected.equals(dbProfile.version())) {
      throw new GlobalOpenCopException(
          "Mapper changed since you loaded it (current=" + dbProfile.version() + ")",
          HttpStatus.CONFLICT);
    }

    if (Boolean.FALSE.equals(dbProfile.editable())) {
      throw new GlobalOpenCopException(
          "The mapper does not allow to be updated", HttpStatus.BAD_REQUEST);
    }

    var newName = meta.profile().name();
    if (!newName.equals(dbProfile.name()) && this.service.mapperProfileExistsByName(newName)) {
      throw new GlobalOpenCopException("Mapper name already taken", HttpStatus.CONFLICT);
    }

    // Payload merge — if the caller omitted payload (sample file unchanged), carry over
    // the existing bytes and filename from DB rather than overwriting with null.
    byte[] resolvedPayload = meta.payload();
    String resolvedFilename = meta.filename();
    var payloadSupplied = resolvedPayload != null && resolvedPayload.length > 0;
    if (!payloadSupplied) {
      var existing = this.service.getLatestMapperProfileFull(meta.profile().id());
      if (existing != null) {
        resolvedPayload = existing.payload();
        resolvedFilename = existing.filename();
      }
    }

    // A supplied payload counts as a change without being compared: the stored bytes are only read
    // when the caller omits them, and fetching a sample payload just to find it identical would
    // cost more than the version it saves.
    var change = this.projection.patch(dbProfile, meta.profile(), payloadSupplied);
    if (change.isEmpty()) {
      // Nothing moved, so there is no version worth minting and nothing to tell an administrator.
      return;
    }

    this.service.createNewVersion(
        new MapperProfileFullMeta(meta.profile(), resolvedFilename, resolvedPayload),
        updatedBy.userId());

    this.auditRecorder.record(
        new AuditEvent(
            EntityType.MAPPER_PROFILE,
            meta.profile().id(),
            newName,
            Operation.PATCH,
            updatedBy,
            change.get(),
            AuditEvent.CURRENT_CHANGE_VERSION));
  }
}
