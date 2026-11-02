// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.business.model.Caller;
import com.opencop.core.api.general.business.model.audit.AuditEntry;
import com.opencop.core.api.general.business.model.audit.AuditEvent;
import com.opencop.core.api.general.business.usecase.profile.mapping.MapperAuditProjection;
import com.opencop.core.api.general.business.usecase.profile.mapping.SaveMappingProfileUsecase;
import com.opencop.core.api.general.ports.gateway.audit.AuditRecorder;
import com.opencop.core.sdk.business.model.profile.MapperProfile;
import com.opencop.core.sdk.business.model.profile.MapperProfileFullMeta;
import com.opencop.core.sdk.business.model.profile.MapperQualifier;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.ports.gateway.mapper.MapperProfileService;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

class SaveMappingProfileUsecaseTest {

  private static final UUID MAPPER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID ACTOR_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final Caller ACTOR =
      new Caller(ACTOR_ID, "Mapper Admin", "admin@example.test", true);

  private MapperProfileService service;
  private AuditRecorder auditRecorder;
  private SaveMappingProfileUsecase usecase;

  @BeforeEach
  void setUp() {
    service = mock(MapperProfileService.class);
    auditRecorder = mock(AuditRecorder.class);
    usecase = new SaveMappingProfileUsecase(service, auditRecorder, new MapperAuditProjection());
  }

  // --- create() ---

  @Test
  void create_whenNameAlreadyTaken_throws409() {
    var meta = fullMeta(MAPPER_ID, "existing-name", 1, true, new byte[] {1}, "file.xml");
    when(service.mapperProfileExistsByName("existing-name")).thenReturn(true);

    var ex = assertThrows(GlobalOpenCopException.class, () -> usecase.create(meta, ACTOR));

    assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
    verify(service, never()).createMapperProfile(any(), any(), any(), any());
  }

  @Test
  void create_whenNameAvailable_delegatesToServiceAndReturnsId() {
    var meta = fullMeta(MAPPER_ID, "new-mapper", 1, true, new byte[] {1, 2}, "sample.xml");
    when(service.mapperProfileExistsByName("new-mapper")).thenReturn(false);
    when(service.createMapperProfile(meta.profile(), meta.payload(), meta.filename(), ACTOR_ID))
        .thenReturn(MAPPER_ID);

    UUID result = usecase.create(meta, ACTOR);

    assertThat(result).isEqualTo(MAPPER_ID);
    verify(service).createMapperProfile(meta.profile(), meta.payload(), meta.filename(), ACTOR_ID);
  }

  @Test
  void create_alwaysWritesAnIngressMapperWhateverTheRequestSays() {
    var requested =
        MapperProfile.builder()
            .withId(MAPPER_ID)
            .withName("new-mapper")
            .withQualifier(MapperQualifier.EGRESS)
            .withVersion(1)
            .withArchived(false)
            .build();
    var profileCaptor = ArgumentCaptor.forClass(MapperProfile.class);
    when(service.createMapperProfile(profileCaptor.capture(), any(), any(), any()))
        .thenReturn(MAPPER_ID);

    usecase.create(new MapperProfileFullMeta(requested, "sample.xml", new byte[] {1}), ACTOR);

    assertThat(profileCaptor.getValue().qualifier()).isEqualTo(MapperQualifier.INGRESS);
  }

  @Test
  void create_startsAtTheInitialVersion() {
    var requested =
        MapperProfile.builder()
            .withId(MAPPER_ID)
            .withName("new-mapper")
            .withDescription("desc")
            .withEditable(true)
            .withVersion(37)
            .withArchived(false)
            .build();
    var meta = new MapperProfileFullMeta(requested, "sample.xml", new byte[] {1});
    var profileCaptor = ArgumentCaptor.forClass(MapperProfile.class);
    when(service.createMapperProfile(profileCaptor.capture(), any(), any(), any()))
        .thenReturn(MAPPER_ID);

    usecase.create(meta, ACTOR);

    assertThat(profileCaptor.getValue().version()).isEqualTo(1);
  }

  // --- update() ---

  @Test
  void update_whenMapperNotFound_throws404() {
    var meta = fullMeta(MAPPER_ID, "mapper", 1, true, new byte[] {1}, "file.xml");
    when(service.lockAndGetLatestMapperProfileMeta(MAPPER_ID)).thenReturn(null);

    var ex = assertThrows(GlobalOpenCopException.class, () -> usecase.update(meta, ACTOR));

    assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    verify(service, never()).createNewVersion(any(), any());
  }

  @Test
  void update_whenVersionMismatches_throws409() {
    var meta = fullMeta(MAPPER_ID, "mapper", 1, true, new byte[] {1}, "file.xml");
    when(service.lockAndGetLatestMapperProfileMeta(MAPPER_ID))
        .thenReturn(profileMeta(MAPPER_ID, "mapper", 2, true));

    var ex = assertThrows(GlobalOpenCopException.class, () -> usecase.update(meta, ACTOR));

    assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
    verify(service, never()).createNewVersion(any(), any());
  }

  @Test
  void update_whenVersionIsNull_skipsConcurrencyCheckAndProceeds() {
    // Null version means the caller opted out of the guard (back-compat)
    var profile =
        MapperProfile.builder()
            .withId(MAPPER_ID)
            .withName("mapper")
            .withDescription("desc")
            .withEditable(true)
            .withVersion(null)
            .withArchived(false)
            .build();
    var meta = new MapperProfileFullMeta(profile, "file.xml", new byte[] {1});
    when(service.lockAndGetLatestMapperProfileMeta(MAPPER_ID))
        .thenReturn(profileMeta(MAPPER_ID, "mapper", 5, true));
    when(service.createNewVersion(any(), any())).thenReturn(meta);

    usecase.update(meta, ACTOR);

    verify(service).createNewVersion(any(), any());
  }

  @Test
  void update_whenNotEditable_throws400() {
    var meta = fullMeta(MAPPER_ID, "mapper", 1, true, new byte[] {1}, "file.xml");
    when(service.lockAndGetLatestMapperProfileMeta(MAPPER_ID))
        .thenReturn(profileMeta(MAPPER_ID, "mapper", 1, false));

    var ex = assertThrows(GlobalOpenCopException.class, () -> usecase.update(meta, ACTOR));

    assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    verify(service, never()).createNewVersion(any(), any());
  }

  @Test
  void update_whenNameChangedAndAlreadyTaken_throws409() {
    var meta = fullMeta(MAPPER_ID, "new-name", 1, true, new byte[] {1}, "file.xml");
    when(service.lockAndGetLatestMapperProfileMeta(MAPPER_ID))
        .thenReturn(profileMeta(MAPPER_ID, "old-name", 1, true));
    when(service.mapperProfileExistsByName("new-name")).thenReturn(true);

    var ex = assertThrows(GlobalOpenCopException.class, () -> usecase.update(meta, ACTOR));

    assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
    verify(service, never()).createNewVersion(any(), any());
  }

  @Test
  void update_whenNameUnchanged_skipsNameUniquenessCheck() {
    var meta = fullMeta(MAPPER_ID, "same-name", 1, true, new byte[] {1}, "file.xml");
    when(service.lockAndGetLatestMapperProfileMeta(MAPPER_ID))
        .thenReturn(profileMeta(MAPPER_ID, "same-name", 1, true));
    when(service.createNewVersion(any(), any())).thenReturn(meta);

    usecase.update(meta, ACTOR);

    verify(service, never()).mapperProfileExistsByName(any());
    verify(service).createNewVersion(any(), any());
  }

  @Test
  void update_whenPayloadProvided_usesProvidedPayloadAndFilenameWithoutFetchingExisting() {
    byte[] payload = {10, 20, 30};
    var meta = fullMeta(MAPPER_ID, "mapper", 1, true, payload, "sample.xml");
    when(service.lockAndGetLatestMapperProfileMeta(MAPPER_ID))
        .thenReturn(profileMeta(MAPPER_ID, "mapper", 1, true));
    var captor = ArgumentCaptor.forClass(MapperProfileFullMeta.class);
    when(service.createNewVersion(captor.capture(), any())).thenReturn(meta);

    usecase.update(meta, ACTOR);

    verify(service, never()).getLatestMapperProfileFull(any());
    var captured = captor.getValue();
    assertThat(Arrays.equals(captured.payload(), payload)).isTrue();
    assertThat(captured.filename()).isEqualTo("sample.xml");
  }

  @Test
  void update_whenPayloadIsNull_fetchesExistingAndMergesPayload() {
    byte[] existingPayload = {99, 88};
    var meta = renamedDescription(fullMeta(MAPPER_ID, "mapper", 1, true, null, null));
    var existingFull = fullMeta(MAPPER_ID, "mapper", 1, true, existingPayload, "original.xml");
    when(service.lockAndGetLatestMapperProfileMeta(MAPPER_ID))
        .thenReturn(profileMeta(MAPPER_ID, "mapper", 1, true));
    when(service.getLatestMapperProfileFull(MAPPER_ID)).thenReturn(existingFull);
    var captor = ArgumentCaptor.forClass(MapperProfileFullMeta.class);
    when(service.createNewVersion(captor.capture(), any())).thenReturn(existingFull);

    usecase.update(meta, ACTOR);

    verify(service).getLatestMapperProfileFull(MAPPER_ID);
    var captured = captor.getValue();
    assertThat(Arrays.equals(captured.payload(), existingPayload)).isTrue();
    assertThat(captured.filename()).isEqualTo("original.xml");
  }

  @Test
  void update_whenPayloadIsEmpty_fetchesExistingAndMergesPayload() {
    byte[] existingPayload = {77, 66};
    var meta = renamedDescription(fullMeta(MAPPER_ID, "mapper", 1, true, new byte[0], null));
    var existingFull = fullMeta(MAPPER_ID, "mapper", 1, true, existingPayload, "existing.xml");
    when(service.lockAndGetLatestMapperProfileMeta(MAPPER_ID))
        .thenReturn(profileMeta(MAPPER_ID, "mapper", 1, true));
    when(service.getLatestMapperProfileFull(MAPPER_ID)).thenReturn(existingFull);
    var captor = ArgumentCaptor.forClass(MapperProfileFullMeta.class);
    when(service.createNewVersion(captor.capture(), any())).thenReturn(existingFull);

    usecase.update(meta, ACTOR);

    verify(service).getLatestMapperProfileFull(MAPPER_ID);
    var captured = captor.getValue();
    assertThat(Arrays.equals(captured.payload(), existingPayload)).isTrue();
    assertThat(captured.filename()).isEqualTo("existing.xml");
  }

  // --- audit ---

  @Test
  void update_whenNothingMoved_writesNeitherAVersionNorAHistoryRow() {
    // Identical to what is stored, and no payload supplied: there is nothing to version and
    // nothing to tell an administrator.
    var meta = fullMeta(MAPPER_ID, "mapper", 1, true, null, null);
    when(service.lockAndGetLatestMapperProfileMeta(MAPPER_ID))
        .thenReturn(profileMeta(MAPPER_ID, "mapper", 1, true));
    when(service.getLatestMapperProfileFull(MAPPER_ID)).thenReturn(meta);

    usecase.update(meta, ACTOR);

    verify(service, never()).createNewVersion(any(), any());
    verify(auditRecorder, never()).record(any());
  }

  @Test
  void update_whenOnlyTheSamplePayloadIsSupplied_stillVersionsAndRecords() {
    // A supplied payload is taken as a change without being compared, so re-uploading identical
    // bytes costs a version rather than a read of the stored payload.
    var meta = fullMeta(MAPPER_ID, "mapper", 1, true, new byte[] {5, 6}, "sample.xml");
    when(service.lockAndGetLatestMapperProfileMeta(MAPPER_ID))
        .thenReturn(profileMeta(MAPPER_ID, "mapper", 1, true));
    when(service.createNewVersion(any(), any())).thenReturn(meta);

    usecase.update(meta, ACTOR);

    verify(service).createNewVersion(any(), any());
    verify(service, never()).getLatestMapperProfileFull(any());
    verify(auditRecorder).record(any());
  }

  @Test
  void update_recordsTheMovedFieldsWithoutTheRulesOrPayload() {
    var meta = renamedDescription(fullMeta(MAPPER_ID, "mapper", 1, true, null, null));
    when(service.lockAndGetLatestMapperProfileMeta(MAPPER_ID))
        .thenReturn(profileMeta(MAPPER_ID, "mapper", 1, true));
    when(service.getLatestMapperProfileFull(MAPPER_ID)).thenReturn(meta);
    when(service.createNewVersion(any(), any())).thenReturn(meta);

    usecase.update(meta, ACTOR);

    var captor = ArgumentCaptor.forClass(AuditEvent.class);
    verify(auditRecorder).record(captor.capture());
    var event = captor.getValue();
    assertThat(event.entityType()).isEqualTo(AuditEntry.EntityType.MAPPER_PROFILE);
    assertThat(event.operation()).isEqualTo(AuditEntry.Operation.PATCH);
    assertThat(event.entityId()).isEqualTo(MAPPER_ID);
    assertThat(event.actor()).isEqualTo(ACTOR);
    assertThat(event.change().changed())
        .singleElement()
        .satisfies(
            moved -> {
              assertThat(moved.field()).isEqualTo("description");
              assertThat(moved.after()).isEqualTo("a new description");
            });
    // The decoding rules and the sample payload are reported as bare facts, never carried.
    assertThat(event.change().details())
        .containsOnlyKeys("rulesChanged", "samplePayloadChanged", "versionBefore", "versionAfter")
        .containsEntry("rulesChanged", false);
  }

  @Test
  void create_recordsTheNewMapper() {
    var meta = fullMeta(MAPPER_ID, "new-mapper", 1, true, new byte[] {1}, "sample.xml");
    when(service.mapperProfileExistsByName("new-mapper")).thenReturn(false);
    when(service.createMapperProfile(any(), any(), any(), any())).thenReturn(MAPPER_ID);

    usecase.create(meta, ACTOR);

    var captor = ArgumentCaptor.forClass(AuditEvent.class);
    verify(auditRecorder).record(captor.capture());
    assertThat(captor.getValue().operation()).isEqualTo(AuditEntry.Operation.CREATE);
    assertThat(captor.getValue().entityName()).isEqualTo("new-mapper");
    assertThat(captor.getValue().actor()).isEqualTo(ACTOR);
  }

  // --- helpers ---

  private static MapperProfile profileMeta(UUID id, String name, int version, boolean editable) {
    return MapperProfile.builder()
        .withId(id)
        .withName(name)
        .withDescription("desc")
        .withEditable(editable)
        .withVersion(version)
        .withArchived(false)
        .build();
  }

  /** The same save with a moved description, so the merge path runs instead of no-opping. */
  private static MapperProfileFullMeta renamedDescription(MapperProfileFullMeta meta) {
    return new MapperProfileFullMeta(
        meta.profile().toBuilder().withDescription("a new description").build(),
        meta.filename(),
        meta.payload());
  }

  private static MapperProfileFullMeta fullMeta(
      UUID id, String name, int version, boolean editable, byte[] payload, String filename) {
    return new MapperProfileFullMeta(profileMeta(id, name, version, editable), filename, payload);
  }
}
