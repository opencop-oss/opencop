// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.watchbox.business.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.opencop.core.api.general.ports.usecase.runtimefeature.CheckRuntimeFeatureAvailability;
import com.opencop.core.api.watchbox.business.factory.WatchboxConfigFactory;
import com.opencop.core.api.watchbox.business.model.WatchboxConfig;
import com.opencop.core.api.watchbox.business.model.WatchboxLayerRole;
import com.opencop.core.api.watchbox.business.model.WatchboxLayerState;
import com.opencop.core.api.watchbox.business.model.WatchboxSettings;
import com.opencop.core.api.watchbox.business.policy.WatchboxLayerAccessPolicy;
import com.opencop.core.api.watchbox.business.policy.WatchboxPatchPolicy;
import com.opencop.core.api.watchbox.business.service.WatchboxDetectionLivenessService;
import com.opencop.core.api.watchbox.business.service.WatchboxDevStatusOverrideService;
import com.opencop.core.api.watchbox.business.service.WatchboxException;
import com.opencop.core.api.watchbox.business.service.WatchboxResponseAssembler;
import com.opencop.core.api.watchbox.business.service.WatchboxSourceEligibilityService;
import com.opencop.core.api.watchbox.business.service.WatchboxValidationService;
import com.opencop.core.api.watchbox.ports.gateway.WatchboxConfigAuditRecorder;
import com.opencop.core.api.watchbox.ports.gateway.WatchboxConfigRepository;
import com.opencop.core.api.watchbox.ports.gateway.WatchboxLayerAccess;
import com.opencop.core.api.watchbox.ports.usecase.WatchboxCaller;
import com.opencop.core.sdk.model.runtimefeature.BackendRuntimeFeature;
import com.opencop.core.sdk.model.watchbox.WatchboxCreateRequest;
import com.opencop.core.sdk.model.watchbox.WatchboxDirection;
import com.opencop.core.sdk.model.watchbox.WatchboxErrorCode;
import com.opencop.core.sdk.model.watchbox.WatchboxFilter;
import com.opencop.core.sdk.model.watchbox.WatchboxPatchRequest;
import com.opencop.core.sdk.model.watchbox.WatchboxResetReason;
import com.opencop.core.sdk.model.watchbox.WatchboxResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.geojson.LngLatAlt;
import org.geojson.Polygon;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
class ManageWatchboxesUsecaseTest {

  @Mock WatchboxConfigRepository repository;
  @Mock WatchboxValidationService validationService;
  @Mock WatchboxSourceEligibilityService sourceEligibilityService;
  @Mock WatchboxResponseAssembler responseAssembler;
  @Mock WatchboxLayerAccess layerAccess;
  @Mock WatchboxLayerAccessPolicy layerAccessPolicy;
  @Mock CheckRuntimeFeatureAvailability runtimeFeatureAvailability;
  @Mock WatchboxDetectionLivenessService detectionLiveness;
  @Mock WatchboxConfigAuditRecorder auditRecorder;
  @Mock WatchboxDevStatusOverrideService devStatusOverrideService;

  private final UUID watchboxId = UUID.randomUUID();
  private final UUID layerId = UUID.randomUUID();
  private final UUID userId = UUID.randomUUID();
  private final Polygon polygon = square();
  private ManageWatchboxesUsecase usecase;
  private Logger logger;
  private ListAppender<ILoggingEvent> appender;

  @BeforeEach
  void setUp() {
    logger = (Logger) LoggerFactory.getLogger(ManageWatchboxesUsecase.class);
    appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    lenient()
        .when(responseAssembler.assembleVisible(any(), any(WatchboxCaller.class), any()))
        .thenReturn(Optional.of(org.mockito.Mockito.mock(WatchboxResponse.class)));
    usecase = usecase(true);
  }

  @AfterEach
  void tearDown() {
    logger.detachAppender(appender);
    appender.stop();
  }

  @Test
  void patchPolygonWithoutConfirmationRequiresResetConfirmation() {
    var caller = new WatchboxCaller(userId, false);
    when(repository.findActiveForUpdate(watchboxId)).thenReturn(Optional.of(existing(true)));
    when(validationService.validateAndNormalizePolygon(any())).thenReturn(movedPolygon());

    var request =
        new WatchboxPatchRequest(null, null, movedPolygon(), null, null, null, null, false);

    assertThatThrownBy(() -> usecase.patch(watchboxId, request, caller))
        .isInstanceOfSatisfying(
            WatchboxException.class,
            ex -> {
              assertThat(ex.code().name())
                  .isEqualTo("WATCHBOX_TRACKING_RESET_CONFIRMATION_REQUIRED");
              assertThat(ex.fields()).contains("polygon");
              assertThat(ex.reasons()).contains("POLYGON_CHANGED");
            });
    verify(auditRecorder, never()).recordPatch(any(), any(), any(), any());
  }

  @Test
  void patchIdenticalPolygonDoesNotRequireResetConfirmation() {
    var caller = new WatchboxCaller(userId, false);
    when(repository.findActiveForUpdate(watchboxId)).thenReturn(Optional.of(existing(true)));
    when(validationService.validateAndNormalizePolygon(any())).thenReturn(square());
    when(repository.update(any())).thenAnswer(invocation -> invocation.getArgument(0));

    usecase.patch(
        watchboxId,
        new WatchboxPatchRequest(null, null, square(), null, null, null, null, null),
        caller);

    var captor = ArgumentCaptor.forClass(WatchboxConfig.class);
    verify(repository).update(captor.capture());
    assertThat(captor.getValue().configEpoch()).isEqualTo(7L);
    var auditBefore = ArgumentCaptor.forClass(WatchboxConfig.class);
    var auditAfter = ArgumentCaptor.forClass(WatchboxConfig.class);
    verify(auditRecorder)
        .recordPatch(auditBefore.capture(), auditAfter.capture(), eq(List.of()), eq(caller));
    assertThat(auditAfter.getValue())
        .usingRecursiveComparison()
        .ignoringFields("updatedAt")
        .withComparatorForType(Double::compare, Double.class)
        .isEqualTo(auditBefore.getValue());
    assertThat(auditAfter.getValue().configEpoch()).isEqualTo(auditBefore.getValue().configEpoch());
  }

  @Test
  void patchDirectionOnlyDoesNotIncrementEpoch() {
    var caller = new WatchboxCaller(userId, false);
    when(repository.findActiveForUpdate(watchboxId)).thenReturn(Optional.of(existing(true)));
    when(repository.update(any())).thenAnswer(invocation -> invocation.getArgument(0));

    usecase.patch(
        watchboxId,
        new WatchboxPatchRequest(null, null, null, WatchboxDirection.LEAVE, null, null, null, null),
        caller);

    var captor = ArgumentCaptor.forClass(WatchboxConfig.class);
    verify(repository).update(captor.capture());
    assertThat(captor.getValue().configEpoch()).isEqualTo(7L);
    assertThat(captor.getValue().direction()).isEqualTo(WatchboxDirection.LEAVE);
  }

  @Test
  void successfulCreateLogsBoundedConfigRecord() {
    var caller = new WatchboxCaller(userId, false);
    var request = createRequest(Set.of(layerId));
    when(validationService.validateLayerIds(Set.of(layerId))).thenReturn(Set.of(layerId));
    when(validationService.validateAndNormalizePolygon(polygon)).thenReturn(polygon);
    when(validationService.validateFilter(WatchboxFilter.empty()))
        .thenReturn(WatchboxFilter.empty());
    var saved = existing(true);
    when(repository.create(any(), any())).thenReturn(saved);

    usecase.create(request, caller);

    var callOrder = inOrder(repository, auditRecorder, responseAssembler);
    callOrder.verify(repository).create(any(), eq(userId));
    callOrder.verify(auditRecorder).recordCreate(saved, caller);
    callOrder.verify(responseAssembler).assembleVisible(eq(saved), eq(caller), any());
    var message = onlyLogMessage();
    assertThat(message).contains("WATCHBOX_CONFIG operation=create");
    assertThat(message).contains("watchbox_id=" + watchboxId);
    assertThat(message).contains("actor_user_id=" + userId);
    assertThat(message).contains("enabled=true");
    assertThat(message).contains("layer_count=1");
    assertThat(message).contains("config_epoch=7");
    assertNoPayloadMaterial(message);
  }

  @Test
  void successfulPatchLogsBoundedConfigRecord() {
    var caller = new WatchboxCaller(userId, false);
    when(repository.findActiveForUpdate(watchboxId)).thenReturn(Optional.of(existing(true)));
    when(repository.update(any())).thenAnswer(invocation -> invocation.getArgument(0));

    usecase.patch(
        watchboxId,
        new WatchboxPatchRequest(null, null, null, WatchboxDirection.LEAVE, null, null, null, null),
        caller);

    var message = onlyLogMessage();
    assertThat(message).contains("WATCHBOX_CONFIG operation=patch");
    assertThat(message).contains("watchbox_id=" + watchboxId);
    assertThat(message).contains("actor_user_id=" + userId);
    assertThat(message).contains("enabled=true");
    assertThat(message).contains("layer_count=1");
    assertThat(message).contains("config_epoch_before=7");
    assertThat(message).contains("config_epoch_after=7");
    assertThat(message).contains("reset_reasons=[]");
    assertNoPayloadMaterial(message);
  }

  @Test
  void createReturnsTypedForbiddenAndEmitsNoSuccessLogWhenPostWriteVisibilityIsEmpty() {
    var caller = new WatchboxCaller(userId, false);
    var request = createRequest(Set.of(layerId));
    when(validationService.validateLayerIds(Set.of(layerId))).thenReturn(Set.of(layerId));
    when(validationService.validateAndNormalizePolygon(polygon)).thenReturn(polygon);
    when(validationService.validateFilter(WatchboxFilter.empty()))
        .thenReturn(WatchboxFilter.empty());
    when(repository.create(any(), any())).thenReturn(existing(true));
    when(responseAssembler.assembleVisible(any(), any(WatchboxCaller.class), any()))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> usecase.create(request, caller))
        .isInstanceOfSatisfying(
            WatchboxException.class,
            ex -> {
              assertThat(ex.code()).isEqualTo(WatchboxErrorCode.WATCHBOX_MANAGEMENT_ROLE_REQUIRED);
              assertThat(ex.status()).isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN);
              assertThat(ex.fields()).containsExactly("layerIds");
            });

    assertThat(appender.list).isEmpty();
  }

  @Test
  void patchReturnsTypedForbiddenAndEmitsNoSuccessLogWhenPostWriteVisibilityIsEmpty() {
    var caller = new WatchboxCaller(userId, false);
    when(repository.findActiveForUpdate(watchboxId)).thenReturn(Optional.of(existing(true)));
    when(repository.update(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(responseAssembler.assembleVisible(any(), any(WatchboxCaller.class), any()))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                usecase.patch(
                    watchboxId,
                    new WatchboxPatchRequest(
                        null, null, null, WatchboxDirection.LEAVE, null, null, null, null),
                    caller))
        .isInstanceOfSatisfying(
            WatchboxException.class,
            ex -> {
              assertThat(ex.code()).isEqualTo(WatchboxErrorCode.WATCHBOX_MANAGEMENT_ROLE_REQUIRED);
              assertThat(ex.status()).isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN);
              assertThat(ex.fields()).containsExactly("layerIds");
            });

    assertThat(appender.list).isEmpty();
  }

  @Test
  void successfulDeleteLogsBoundedConfigRecord() {
    var caller = new WatchboxCaller(userId, false);
    var existing = existing(true);
    when(repository.findActiveForUpdate(watchboxId)).thenReturn(Optional.of(existing));

    usecase.delete(watchboxId, caller);

    var callOrder = inOrder(repository, auditRecorder, devStatusOverrideService);
    callOrder.verify(repository).findActiveForUpdate(watchboxId);
    callOrder.verify(repository).archive(watchboxId, userId);
    callOrder.verify(auditRecorder).recordArchive(existing, caller);
    callOrder.verify(devStatusOverrideService).clear(watchboxId);
    var message = onlyLogMessage();
    assertThat(message).contains("WATCHBOX_CONFIG operation=delete");
    assertThat(message).contains("watchbox_id=" + watchboxId);
    assertThat(message).contains("actor_user_id=" + userId);
    assertNoPayloadMaterial(message);
  }

  @Test
  void patchReenableWithConfirmationIncrementsEpochAndRerunsEligibility() {
    var caller = new WatchboxCaller(userId, false);
    when(repository.findActiveForUpdate(watchboxId)).thenReturn(Optional.of(existing(false)));
    when(repository.update(any())).thenAnswer(invocation -> invocation.getArgument(0));

    usecase.patch(
        watchboxId,
        new WatchboxPatchRequest(null, null, null, null, null, null, true, true),
        caller);

    var captor = ArgumentCaptor.forClass(WatchboxConfig.class);
    verify(repository).update(captor.capture());
    var auditBefore = ArgumentCaptor.forClass(WatchboxConfig.class);
    var auditAfter = ArgumentCaptor.forClass(WatchboxConfig.class);
    verify(auditRecorder)
        .recordPatch(
            auditBefore.capture(),
            auditAfter.capture(),
            eq(List.of(WatchboxResetReason.REENABLED)),
            eq(caller));
    verify(sourceEligibilityService).assertEligibleForEnabledWatchbox(Set.of(layerId));
    assertThat(captor.getValue().configEpoch()).isEqualTo(8L);
    assertThat(captor.getValue().enabled()).isTrue();
    assertThat(auditBefore.getValue().configEpoch()).isEqualTo(7L);
    assertThat(auditAfter.getValue()).isEqualTo(captor.getValue());
  }

  @Test
  void patch_whenOnlyDisabling_skipsEnabledValidationAndEligibility() {
    var caller = new WatchboxCaller(userId, false);
    when(repository.findActiveForUpdate(watchboxId)).thenReturn(Optional.of(existing(true)));
    when(repository.update(any())).thenAnswer(invocation -> invocation.getArgument(0));

    usecase.patch(
        watchboxId,
        new WatchboxPatchRequest(null, null, null, null, null, null, false, null),
        caller);

    var captor = ArgumentCaptor.forClass(WatchboxConfig.class);
    verify(repository).update(captor.capture());
    assertThat(captor.getValue().enabled()).isFalse();
    verify(validationService, never()).validateActiveLayers(any());
    verify(sourceEligibilityService, never()).assertEligibleForEnabledWatchbox(any());
  }

  @Test
  void createRejectsWhenGlobalWatchboxFeatureIsDisabled() {
    usecase = usecase(false);
    var caller = new WatchboxCaller(userId, false);

    assertThatThrownBy(() -> usecase.create(org.mockito.Mockito.mock(), caller))
        .isInstanceOfSatisfying(
            WatchboxException.class,
            ex -> assertThat(ex.code().name()).isEqualTo("WATCHBOX_FEATURE_DISABLED"));
  }

  @Test
  void createDeniesManagementBeforeLayerStateDisclosure() {
    var caller = new WatchboxCaller(userId, false);
    var request = createRequest(Set.of(layerId));
    when(validationService.validateLayerIds(Set.of(layerId))).thenReturn(Set.of(layerId));
    doThrow(
            new WatchboxException(
                WatchboxErrorCode.WATCHBOX_MANAGEMENT_ROLE_REQUIRED,
                "Watchbox management role is required",
                org.springframework.http.HttpStatus.FORBIDDEN,
                java.util.List.of("layerIds")))
        .when(layerAccessPolicy)
        .assertCanManage(Set.of(layerId), caller);

    assertThatThrownBy(() -> usecase.create(request, caller))
        .isInstanceOfSatisfying(
            WatchboxException.class,
            ex ->
                assertThat(ex.code())
                    .isEqualTo(WatchboxErrorCode.WATCHBOX_MANAGEMENT_ROLE_REQUIRED));

    verify(validationService, never()).validateActiveLayers(any());
    verify(auditRecorder, never()).recordCreate(any(), any());
  }

  @Test
  void globalAdminCreateReachesLayerStateValidationAfterManagementBypass() {
    var caller = new WatchboxCaller(userId, true);
    var request = createRequest(Set.of(layerId));
    when(validationService.validateLayerIds(Set.of(layerId))).thenReturn(Set.of(layerId));
    when(repository.create(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));

    usecase.create(request, caller);

    verify(layerAccessPolicy).assertCanManage(Set.of(layerId), caller);
    verify(validationService).validateActiveLayers(Set.of(layerId));
  }

  @Test
  void createNullRequestReturnsStructuredWatchboxError() {
    var caller = new WatchboxCaller(userId, false);

    assertThatThrownBy(() -> usecase.create(null, caller))
        .isInstanceOfSatisfying(
            WatchboxException.class,
            ex -> {
              assertThat(ex.code()).isEqualTo(WatchboxErrorCode.WATCHBOX_INVALID_REQUEST);
              assertThat(ex.fields()).containsExactly("body");
            });
  }

  @Test
  void patchPureDisableSucceedsWhenGlobalWatchboxFeatureIsDisabled() {
    usecase = usecase(false);
    var caller = new WatchboxCaller(userId, false);
    when(repository.findActiveForUpdate(watchboxId)).thenReturn(Optional.of(existing(true)));
    when(repository.update(any())).thenAnswer(invocation -> invocation.getArgument(0));

    usecase.patch(
        watchboxId,
        new WatchboxPatchRequest(null, null, null, null, null, null, false, null),
        caller);

    var captor = ArgumentCaptor.forClass(WatchboxConfig.class);
    verify(repository).update(captor.capture());
    assertThat(captor.getValue().enabled()).isFalse();
    verify(sourceEligibilityService, never()).assertEligibleForEnabledWatchbox(any());
  }

  @Test
  void patchNoOpAlreadyDisabledSucceedsWhenGlobalWatchboxFeatureIsDisabled() {
    usecase = usecase(false);
    var caller = new WatchboxCaller(userId, false);
    when(repository.findActiveForUpdate(watchboxId)).thenReturn(Optional.of(existing(false)));
    when(repository.update(any())).thenAnswer(invocation -> invocation.getArgument(0));

    usecase.patch(
        watchboxId,
        new WatchboxPatchRequest(null, null, null, null, null, null, false, null),
        caller);

    var captor = ArgumentCaptor.forClass(WatchboxConfig.class);
    verify(repository).update(captor.capture());
    assertThat(captor.getValue().enabled()).isFalse();
    verify(sourceEligibilityService, never()).assertEligibleForEnabledWatchbox(any());
  }

  @Test
  void patchPureDisable_whenStoredLayerIsMissingOrArchived_allowsCleanup() {
    usecase = usecaseWithLayerState(WatchboxLayerState.MISSING_OR_ARCHIVED);
    var caller = new WatchboxCaller(userId, false);
    when(repository.findActiveForUpdate(watchboxId)).thenReturn(Optional.of(existing(true)));
    when(repository.update(any())).thenAnswer(invocation -> invocation.getArgument(0));

    usecase.patch(
        watchboxId,
        new WatchboxPatchRequest(null, null, null, null, null, null, false, null),
        caller);

    var captor = ArgumentCaptor.forClass(WatchboxConfig.class);
    verify(repository).update(captor.capture());
    assertThat(captor.getValue().enabled()).isFalse();
    verify(layerAccess, atLeastOnce()).rolesForLayers(Set.of(layerId), caller);
    verify(layerAccess, never()).activeLayerState(any());
  }

  @Test
  void delete_whenStoredLayerIsMissingOrArchived_allowsCleanup() {
    usecase = usecaseWithLayerState(WatchboxLayerState.MISSING_OR_ARCHIVED);
    var caller = new WatchboxCaller(userId, false);
    when(repository.findActiveForUpdate(watchboxId)).thenReturn(Optional.of(existing(true)));

    usecase.delete(watchboxId, caller);

    verify(repository).archive(watchboxId, userId);
    verify(layerAccess, atLeastOnce()).rolesForLayers(Set.of(layerId), caller);
    verify(layerAccess, never()).activeLayerState(any());
  }

  @Test
  void patchDisablePlusEdit_whenStoredLayerIsMissingOrArchived_rejects() {
    usecase = usecaseWithLayerState(WatchboxLayerState.MISSING_OR_ARCHIVED);
    var caller = new WatchboxCaller(userId, false);
    when(repository.findActiveForUpdate(watchboxId)).thenReturn(Optional.of(existing(true)));

    assertThatThrownBy(
            () ->
                usecase.patch(
                    watchboxId,
                    new WatchboxPatchRequest("renamed", null, null, null, null, null, false, null),
                    caller))
        .isInstanceOfSatisfying(
            WatchboxException.class,
            ex -> {
              assertThat(ex.code()).isEqualTo(WatchboxErrorCode.WATCHBOX_LAYER_REQUIRED);
              assertThat(ex.reasons()).isEmpty();
            });

    verify(layerAccess).activeLayerState(layerId);
    verify(repository, never()).update(any());
  }

  @Test
  void patchReenableRejectsWhenGlobalWatchboxFeatureIsDisabled() {
    usecase = usecase(false);
    var caller = new WatchboxCaller(userId, false);
    when(repository.findActiveForUpdate(watchboxId)).thenReturn(Optional.of(existing(false)));

    assertThatThrownBy(
            () ->
                usecase.patch(
                    watchboxId,
                    new WatchboxPatchRequest(null, null, null, null, null, null, true, null),
                    caller))
        .isInstanceOfSatisfying(
            WatchboxException.class,
            ex -> assertThat(ex.code().name()).isEqualTo("WATCHBOX_FEATURE_DISABLED"));
    verify(sourceEligibilityService, never()).assertEligibleForEnabledWatchbox(any());
  }

  @Test
  void patchDisablePlusEditRejectsWhenGlobalWatchboxFeatureIsDisabled() {
    usecase = usecase(false);
    var caller = new WatchboxCaller(userId, false);
    when(repository.findActiveForUpdate(watchboxId)).thenReturn(Optional.of(existing(true)));

    assertThatThrownBy(
            () ->
                usecase.patch(
                    watchboxId,
                    new WatchboxPatchRequest("renamed", null, null, null, null, null, false, null),
                    caller))
        .isInstanceOfSatisfying(
            WatchboxException.class,
            ex -> assertThat(ex.code().name()).isEqualTo("WATCHBOX_FEATURE_DISABLED"));
    verify(sourceEligibilityService, never()).assertEligibleForEnabledWatchbox(any());
  }

  @Test
  void patchLayerReplacementReturnsFeatureDisabledBeforeNextLayerDisclosureWhenGloballyDisabled() {
    usecase = usecase(false);
    var caller = new WatchboxCaller(userId, false);
    var nextLayerId = UUID.randomUUID();
    when(repository.findActiveForUpdate(watchboxId)).thenReturn(Optional.of(existing(true)));
    when(validationService.validateLayerIds(Set.of(nextLayerId))).thenReturn(Set.of(nextLayerId));

    assertThatThrownBy(
            () ->
                usecase.patch(
                    watchboxId,
                    new WatchboxPatchRequest(
                        null, null, null, null, Set.of(nextLayerId), null, true, null),
                    caller))
        .isInstanceOfSatisfying(
            WatchboxException.class,
            ex -> assertThat(ex.code()).isEqualTo(WatchboxErrorCode.WATCHBOX_FEATURE_DISABLED));

    verify(layerAccessPolicy, never()).assertCanManage(Set.of(nextLayerId), caller);
    verify(validationService, never()).validateActiveLayers(Set.of(nextLayerId));
    verify(sourceEligibilityService, never()).assertEligibleForEnabledWatchbox(any());
  }

  @Test
  void patchNullRequestReturnsStructuredWatchboxError() {
    var caller = new WatchboxCaller(userId, false);

    assertThatThrownBy(() -> usecase.patch(watchboxId, null, caller))
        .isInstanceOfSatisfying(
            WatchboxException.class,
            ex -> {
              assertThat(ex.code()).isEqualTo(WatchboxErrorCode.WATCHBOX_INVALID_REQUEST);
              assertThat(ex.fields()).containsExactly("body");
            });
  }

  private ManageWatchboxesUsecase usecase(boolean enabled) {
    lenient()
        .when(runtimeFeatureAvailability.isAvailable(any(BackendRuntimeFeature.class), any()))
        .thenReturn(enabled);
    return new ManageWatchboxesUsecase(
        repository,
        validationService,
        sourceEligibilityService,
        responseAssembler,
        layerAccessPolicy,
        new WatchboxConfigFactory(),
        new WatchboxPatchPolicy(),
        runtimeFeatureAvailability,
        detectionLiveness,
        auditRecorder,
        Optional.of(devStatusOverrideService));
  }

  private ManageWatchboxesUsecase usecaseWithLayerState(WatchboxLayerState layerState) {
    var settings = new WatchboxSettings(2_000);
    lenient()
        .when(runtimeFeatureAvailability.isAvailable(any(BackendRuntimeFeature.class), any()))
        .thenReturn(true);
    lenient().when(layerAccess.activeLayerState(layerId)).thenReturn(layerState);
    lenient()
        .when(layerAccess.rolesForLayers(Set.of(layerId), new WatchboxCaller(userId, false)))
        .thenReturn(Map.of(layerId, WatchboxLayerRole.OWNER));
    return new ManageWatchboxesUsecase(
        repository,
        new WatchboxValidationService(settings, layerAccess),
        sourceEligibilityService,
        responseAssembler,
        new WatchboxLayerAccessPolicy(layerAccess),
        new WatchboxConfigFactory(),
        new WatchboxPatchPolicy(),
        runtimeFeatureAvailability,
        detectionLiveness,
        auditRecorder,
        Optional.of(devStatusOverrideService));
  }

  private WatchboxConfig existing(boolean enabled) {
    return new WatchboxConfig(
        watchboxId,
        "watch",
        null,
        enabled,
        WatchboxDirection.BOTH,
        polygon,
        WatchboxFilter.empty(),
        Set.of(layerId),
        7L,
        Instant.parse("2026-06-26T01:00:00Z"),
        Instant.parse("2026-06-26T01:00:00Z"));
  }

  private String onlyLogMessage() {
    assertThat(appender.list).hasSize(1);
    return appender.list.getFirst().getFormattedMessage();
  }

  private void assertNoPayloadMaterial(String message) {
    assertThat(message).doesNotContain("REST/config proof");
    assertThat(message).doesNotContain("-100.2");
    assertThat(message).doesNotContain("10061000001211000000");
    assertThat(message).doesNotContain("{\"");
  }

  private WatchboxCreateRequest createRequest(Set<UUID> layerIds) {
    return new WatchboxCreateRequest(
        "watch", null, polygon, WatchboxDirection.BOTH, layerIds, WatchboxFilter.empty(), true);
  }

  private Polygon movedPolygon() {
    return new Polygon(
        java.util.List.of(
            new LngLatAlt(-100.1, 39.9),
            new LngLatAlt(-99.9, 39.9),
            new LngLatAlt(-99.9, 40.1),
            new LngLatAlt(-100.1, 40.1),
            new LngLatAlt(-100.1, 39.9)));
  }

  private Polygon square() {
    return new Polygon(
        java.util.List.of(
            new LngLatAlt(-100.2, 39.8),
            new LngLatAlt(-99.8, 39.8),
            new LngLatAlt(-99.8, 40.2),
            new LngLatAlt(-100.2, 40.2),
            new LngLatAlt(-100.2, 39.8)));
  }
}
