// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.business.model.Caller;
import com.opencop.core.api.general.business.model.audit.AuditEntry.EntityType;
import com.opencop.core.api.general.business.model.audit.AuditEntry.Operation;
import com.opencop.core.api.general.business.model.audit.AuditEvent;
import com.opencop.core.api.general.business.usecase.profile.mapping.DeleteMappingProfileUsecase;
import com.opencop.core.api.general.business.usecase.profile.mapping.MapperAuditProjection;
import com.opencop.core.api.general.ports.gateway.audit.AuditRecorder;
import com.opencop.core.sdk.business.model.profile.MapperProfile;
import com.opencop.core.sdk.business.model.profile.MapperQualifier;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.ports.gateway.mapper.MapperProfileService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

class DeleteMappingProfileUsecaseTest {

  private static final UUID MAPPER_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");
  private static final UUID ACTOR_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");
  private static final Caller ACTOR =
      new Caller(ACTOR_ID, "Mapper Admin", "admin@example.test", true);

  private MapperProfileService service;
  private AuditRecorder auditRecorder;
  private DeleteMappingProfileUsecase usecase;

  @BeforeEach
  void setUp() {
    service = mock(MapperProfileService.class);
    auditRecorder = mock(AuditRecorder.class);
    usecase = new DeleteMappingProfileUsecase(service, auditRecorder, new MapperAuditProjection());
  }

  @Test
  void archive_whenNotFound_throws404() {
    when(service.mapperProfileExists(MAPPER_ID)).thenReturn(false);

    var ex = assertThrows(GlobalOpenCopException.class, () -> usecase.archive(MAPPER_ID, ACTOR));

    assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    verify(service, never()).archiveMapperProfile(MAPPER_ID, ACTOR_ID);
    verifyNoInteractions(auditRecorder);
  }

  @Test
  void archive_whenAlreadyArchived_throws400() {
    when(service.mapperProfileExists(MAPPER_ID)).thenReturn(true);
    when(service.lockAndGetLatestMapperProfileMeta(MAPPER_ID)).thenReturn(mapper(true, true));

    var ex = assertThrows(GlobalOpenCopException.class, () -> usecase.archive(MAPPER_ID, ACTOR));

    assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    verify(service, never()).archiveMapperProfile(MAPPER_ID, ACTOR_ID);
    verifyNoInteractions(auditRecorder);
  }

  @Test
  void archive_whenNotEditable_throws400() {
    when(service.mapperProfileExists(MAPPER_ID)).thenReturn(true);
    when(service.lockAndGetLatestMapperProfileMeta(MAPPER_ID)).thenReturn(mapper(false, false));

    var ex = assertThrows(GlobalOpenCopException.class, () -> usecase.archive(MAPPER_ID, ACTOR));

    assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    verify(service, never()).archiveMapperProfile(MAPPER_ID, ACTOR_ID);
    verifyNoInteractions(auditRecorder);
  }

  @Test
  void archive_whenAConnectionStillUsesTheMapper_throws409() {
    when(service.mapperProfileExists(MAPPER_ID)).thenReturn(true);
    when(service.lockAndGetLatestMapperProfileMeta(MAPPER_ID)).thenReturn(mapper(false, true));
    when(service.archiveMapperProfile(MAPPER_ID, ACTOR_ID)).thenReturn(0);

    var ex = assertThrows(GlobalOpenCopException.class, () -> usecase.archive(MAPPER_ID, ACTOR));

    assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(ex.getMessage()).contains("linked");
    verifyNoInteractions(auditRecorder);
  }

  @Test
  void archive_happyPath_callsServiceAndSucceeds() {
    when(service.mapperProfileExists(MAPPER_ID)).thenReturn(true);
    when(service.lockAndGetLatestMapperProfileMeta(MAPPER_ID)).thenReturn(mapper(false, true));
    when(service.archiveMapperProfile(MAPPER_ID, ACTOR_ID)).thenReturn(1);

    usecase.archive(MAPPER_ID, ACTOR);

    verify(service).archiveMapperProfile(MAPPER_ID, ACTOR_ID);
    var recorded = ArgumentCaptor.forClass(AuditEvent.class);
    verify(auditRecorder).record(recorded.capture());
    var event = recorded.getValue();
    assertThat(event.entityType()).isEqualTo(EntityType.MAPPER_PROFILE);
    assertThat(event.entityId()).isEqualTo(MAPPER_ID);
    assertThat(event.entityName()).isEqualTo("Test Mapper");
    assertThat(event.operation()).isEqualTo(Operation.ARCHIVE);
    assertThat(event.actor()).isEqualTo(ACTOR);
  }

  @Test
  void archive_whenEgressMapper_throws400BeforeAnyOtherCheck() {
    when(service.mapperProfileExists(MAPPER_ID)).thenReturn(true);
    when(service.lockAndGetLatestMapperProfileMeta(MAPPER_ID))
        .thenReturn(mapper(false, true).toBuilder().withQualifier(MapperQualifier.EGRESS).build());

    var ex = assertThrows(GlobalOpenCopException.class, () -> usecase.archive(MAPPER_ID, ACTOR));

    assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(ex.getMessage()).contains("egress mapper");
    verify(service, never()).archiveMapperProfile(MAPPER_ID, ACTOR_ID);
    verifyNoInteractions(auditRecorder);
  }

  private static MapperProfile mapper(boolean archived, boolean editable) {
    return MapperProfile.builder()
        .withId(MAPPER_ID)
        .withName("Test Mapper")
        .withDescription(null)
        .withEditable(editable)
        .withVersion(1)
        .withArchived(archived)
        .build();
  }
}
