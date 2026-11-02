// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.watchbox.business.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.api.watchbox.business.model.WatchboxConfig;
import com.opencop.core.api.watchbox.business.model.WatchboxLayerRole;
import com.opencop.core.api.watchbox.business.model.WatchboxSourceStatusRecord;
import com.opencop.core.api.watchbox.business.policy.WatchboxLayerAccessPolicy;
import com.opencop.core.api.watchbox.ports.gateway.WatchboxConfigRepository;
import com.opencop.core.api.watchbox.ports.gateway.WatchboxLayerAccess;
import com.opencop.core.api.watchbox.ports.usecase.WatchboxCaller;
import com.opencop.core.sdk.model.connection.spec.ConnectionSpec;
import com.opencop.core.sdk.model.connection.spec.security.NoSecurity;
import com.opencop.core.sdk.model.connection.spec.transport.TcpConfig;
import com.opencop.core.sdk.model.datasource.AccessMode;
import com.opencop.core.sdk.model.datasource.DataSourceReadFilter;
import com.opencop.core.sdk.model.datasource.SocketDataSource;
import com.opencop.core.sdk.model.visibility.RecordVisibility;
import com.opencop.core.sdk.model.watchbox.WatchboxAlertingStatus;
import com.opencop.core.sdk.model.watchbox.WatchboxCapabilities;
import com.opencop.core.sdk.model.watchbox.WatchboxDirection;
import com.opencop.core.sdk.model.watchbox.WatchboxFilter;
import com.opencop.core.sdk.model.watchbox.WatchboxResponse;
import com.opencop.core.sdk.model.watchbox.WatchboxStatusReason;
import com.opencop.core.sdk.ports.gateway.api.WatchboxDetectionLivenessReader.DetectionLiveness;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.geojson.LngLatAlt;
import org.geojson.Polygon;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WatchboxResponseAssemblerTest {

  @Mock WatchboxConfigRepository repository;
  @Mock DataSourceRepository dataSourceRepository;
  @Mock WatchboxLayerAccess layerAccess;

  private final UUID watchboxId = UUID.randomUUID();
  private final UUID layerId = UUID.randomUUID();
  private final UUID hiddenLayerId = UUID.randomUUID();
  private final UUID userId = UUID.randomUUID();
  private final UUID sourceId = UUID.randomUUID();
  private WatchboxResponseAssembler assembler;

  @BeforeEach
  void setUp() {
    assembler =
        new WatchboxResponseAssembler(
            repository,
            dataSourceRepository,
            new WatchboxLayerAccessPolicy(layerAccess),
            Optional.empty());
  }

  @Test
  void assembleVisible_whenCallerManagesAllLayers_includesNamedSourceStatusRollup() {
    var caller = new WatchboxCaller(userId, false);
    when(layerAccess.rolesForLayers(Set.of(layerId), caller))
        .thenReturn(Map.of(layerId, WatchboxLayerRole.OWNER));
    var status =
        new WatchboxSourceStatusRecord(
            sourceId,
            WatchboxAlertingStatus.DEGRADED,
            WatchboxStatusReason.WATCHBOX_MAPPER_OBJECT_ID_NOT_CONFIGURED,
            Instant.parse("2026-06-26T01:00:00Z"),
            1L,
            10L,
            Instant.parse("2026-06-26T01:01:00Z"));
    when(repository.findSourceStatuses(watchboxId, 7L)).thenReturn(List.of(status));
    when(dataSourceRepository.findAll(
            new DataSourceReadFilter(
                null, Set.of(sourceId), RecordVisibility.ALL, null, null, null)))
        .thenReturn(List.of(source(sourceId, "cot-source")));

    var response =
        assembler.assembleVisible(config(true), caller, DetectionLiveness.RUNNING).orElseThrow();

    assertThat(response.alertingStatus()).isEqualTo(WatchboxAlertingStatus.DEGRADED);
    assertThat(response.alertingReasons())
        .containsExactly(WatchboxStatusReason.WATCHBOX_MAPPER_OBJECT_ID_NOT_CONFIGURED);
    assertThat(response.sourceStatuses())
        .singleElement()
        .satisfies(row -> assertThat(row.sourceName()).isEqualTo("cot-source"));
    assertThat(response.capabilities()).isEqualTo(new WatchboxCapabilities(true, true));
    verify(dataSourceRepository)
        .findAll(
            new DataSourceReadFilter(
                null, Set.of(sourceId), RecordVisibility.ALL, null, null, null));
  }

  @Test
  void assembleVisible_whenCallerCanOnlyView_omitsSourceStatuses() {
    var caller = new WatchboxCaller(userId, false);
    when(layerAccess.rolesForLayers(Set.of(layerId), caller))
        .thenReturn(Map.of(layerId, WatchboxLayerRole.MEMBER));
    when(repository.findSourceStatuses(watchboxId, 7L))
        .thenReturn(
            List.of(
                new WatchboxSourceStatusRecord(
                    sourceId,
                    WatchboxAlertingStatus.DEGRADED,
                    WatchboxStatusReason.WATCHBOX_MAPPER_OBJECT_ID_NOT_CONFIGURED,
                    Instant.parse("2026-06-26T01:00:00Z"),
                    1L,
                    10L,
                    Instant.parse("2026-06-26T01:01:00Z"))));

    var response =
        assembler.assembleVisible(config(true), caller, DetectionLiveness.RUNNING).orElseThrow();

    assertThat(response.alertingStatus()).isEqualTo(WatchboxAlertingStatus.DEGRADED);
    assertThat(response.alertingReasons())
        .containsExactly(WatchboxStatusReason.WATCHBOX_MAPPER_OBJECT_ID_NOT_CONFIGURED);
    assertThat(response.sourceStatuses()).isEmpty();
    assertThat(response.capabilities()).isEqualTo(new WatchboxCapabilities(false, false));
  }

  @Test
  void assembleVisible_whenCallerIsGlobalAdmin_managesWithoutRoleReads() {
    var caller = new WatchboxCaller(userId, true);
    when(repository.findSourceStatuses(watchboxId, 7L)).thenReturn(List.of());

    var response =
        assembler.assembleVisible(config(true), caller, DetectionLiveness.RUNNING).orElseThrow();

    assertThat(response.capabilities()).isEqualTo(new WatchboxCapabilities(true, true));
    verifyNoInteractions(layerAccess);
  }

  @Test
  void assembleVisible_whenCallerCannotSeeEveryLayer_returnsEmpty() {
    var caller = new WatchboxCaller(userId, false);
    var layerIds = Set.of(layerId, hiddenLayerId);
    when(layerAccess.rolesForLayers(layerIds, caller))
        .thenReturn(Map.of(layerId, WatchboxLayerRole.MEMBER));
    assertThat(assembler.assembleVisible(config(true, layerIds), caller, DetectionLiveness.RUNNING))
        .isEmpty();
  }

  @Test
  void callerWithNoVisibleSelectedLayersIsNotVisible() {
    var caller = new WatchboxCaller(userId, false);
    var layerIds = Set.of(layerId, hiddenLayerId);
    when(layerAccess.rolesForLayers(layerIds, caller)).thenReturn(Map.of());

    assertThat(assembler.assembleVisible(config(true, layerIds), caller, DetectionLiveness.RUNNING))
        .isEmpty();
  }

  @Test
  void detectionNotRunningDegradesAnOtherwiseHealthyWatchbox() {
    var caller = new WatchboxCaller(userId, false);
    when(layerAccess.rolesForLayers(Set.of(layerId), caller))
        .thenReturn(Map.of(layerId, WatchboxLayerRole.OWNER));
    when(repository.findSourceStatuses(watchboxId, 7L)).thenReturn(List.of());

    var response =
        assembler
            .assembleVisible(config(true), caller, DetectionLiveness.NOT_RUNNING)
            .orElseThrow();

    // The Processor writes only problem rows, so with the Processor down an empty status list
    // would read as ACTIVE while nothing was being detected. Liveness is the input that keeps
    // green honest.
    assertThat(response.alertingStatus()).isEqualTo(WatchboxAlertingStatus.DEGRADED);
    assertThat(response.alertingReasons())
        .containsExactly(WatchboxStatusReason.WATCHBOX_DETECTION_NOT_RUNNING);
  }

  @Test
  void partiallyStalledDetectionDegradesAnOtherwiseHealthyWatchbox() {
    var caller = new WatchboxCaller(userId, false);
    when(layerAccess.rolesForLayers(Set.of(layerId), caller))
        .thenReturn(Map.of(layerId, WatchboxLayerRole.OWNER));
    when(repository.findSourceStatuses(watchboxId, 7L)).thenReturn(List.of());

    var response =
        assembler
            .assembleVisible(config(true), caller, DetectionLiveness.PARTIALLY_STALLED)
            .orElseThrow();

    // Detection is alive but a wedged listener partition stopped advancing past the degrade
    // threshold: alerts on that partition are silently delayed, so ACTIVE would misrepresent.
    assertThat(response.alertingStatus()).isEqualTo(WatchboxAlertingStatus.DEGRADED);
    assertThat(response.alertingReasons())
        .containsExactly(WatchboxStatusReason.WATCHBOX_DETECTION_PARTIALLY_STALLED);
  }

  @Test
  void partiallyStalledDetectionDoesNotMaskAFailedRollup() {
    var caller = new WatchboxCaller(userId, false);
    when(layerAccess.rolesForLayers(Set.of(layerId), caller))
        .thenReturn(Map.of(layerId, WatchboxLayerRole.MEMBER));
    when(repository.findSourceStatuses(watchboxId, 7L))
        .thenReturn(
            List.of(
                new WatchboxSourceStatusRecord(
                    sourceId,
                    WatchboxAlertingStatus.FAILED,
                    WatchboxStatusReason.WATCHBOX_SOURCE_OBJECT_ID_UNSTABLE_OR_UNKNOWN,
                    Instant.parse("2026-06-26T01:00:00Z"),
                    null,
                    null,
                    Instant.parse("2026-06-26T01:01:00Z"))));

    var response =
        assembler
            .assembleVisible(config(true), caller, DetectionLiveness.PARTIALLY_STALLED)
            .orElseThrow();

    assertThat(response.alertingStatus()).isEqualTo(WatchboxAlertingStatus.FAILED);
    assertThat(response.alertingReasons())
        .containsExactlyInAnyOrder(
            WatchboxStatusReason.WATCHBOX_SOURCE_OBJECT_ID_UNSTABLE_OR_UNKNOWN,
            WatchboxStatusReason.WATCHBOX_DETECTION_PARTIALLY_STALLED);
  }

  @Test
  void disabledWatchboxStaysDisabledRatherThanPartiallyStalled() {
    var caller = new WatchboxCaller(userId, false);
    when(layerAccess.rolesForLayers(Set.of(layerId), caller))
        .thenReturn(Map.of(layerId, WatchboxLayerRole.OWNER));
    when(repository.findSourceStatuses(watchboxId, 7L)).thenReturn(List.of());

    var response =
        assembler
            .assembleVisible(config(false), caller, DetectionLiveness.PARTIALLY_STALLED)
            .orElseThrow();

    assertThat(response.alertingStatus()).isEqualTo(WatchboxAlertingStatus.DISABLED);
    assertThat(response.alertingReasons()).containsExactly(WatchboxStatusReason.WATCHBOX_DISABLED);
  }

  @Test
  void detectionNotRunningDoesNotMaskAFailedRollup() {
    var caller = new WatchboxCaller(userId, false);
    when(layerAccess.rolesForLayers(Set.of(layerId), caller))
        .thenReturn(Map.of(layerId, WatchboxLayerRole.MEMBER));
    when(repository.findSourceStatuses(watchboxId, 7L))
        .thenReturn(
            List.of(
                new WatchboxSourceStatusRecord(
                    sourceId,
                    WatchboxAlertingStatus.FAILED,
                    WatchboxStatusReason.WATCHBOX_SOURCE_OBJECT_ID_UNSTABLE_OR_UNKNOWN,
                    Instant.parse("2026-06-26T01:00:00Z"),
                    null,
                    null,
                    Instant.parse("2026-06-26T01:01:00Z"))));

    var response =
        assembler
            .assembleVisible(config(true), caller, DetectionLiveness.NOT_RUNNING)
            .orElseThrow();

    assertThat(response.alertingStatus()).isEqualTo(WatchboxAlertingStatus.FAILED);
    assertThat(response.alertingReasons())
        .containsExactlyInAnyOrder(
            WatchboxStatusReason.WATCHBOX_SOURCE_OBJECT_ID_UNSTABLE_OR_UNKNOWN,
            WatchboxStatusReason.WATCHBOX_DETECTION_NOT_RUNNING);
  }

  @Test
  void disabledWatchboxStaysDisabledRatherThanDetectionDegraded() {
    var caller = new WatchboxCaller(userId, false);
    when(layerAccess.rolesForLayers(Set.of(layerId), caller))
        .thenReturn(Map.of(layerId, WatchboxLayerRole.OWNER));
    when(repository.findSourceStatuses(watchboxId, 7L)).thenReturn(List.of());

    var response =
        assembler
            .assembleVisible(config(false), caller, DetectionLiveness.NOT_RUNNING)
            .orElseThrow();

    // A disabled watchbox is not "degraded by a down detector": the owner asked for no alerting.
    assertThat(response.alertingStatus()).isEqualTo(WatchboxAlertingStatus.DISABLED);
    assertThat(response.alertingReasons()).containsExactly(WatchboxStatusReason.WATCHBOX_DISABLED);
  }

  @Test
  void assembleVisibleAll_mixedCallerRoles_matchesPerConfigAssemblyWithBatchedReads() {
    var caller = new WatchboxCaller(userId, false);
    var managedLayerId = UUID.randomUUID();
    var viewedLayerId = UUID.randomUUID();
    var hiddenBoxLayerId = UUID.randomUUID();
    var managedBox = watchbox(UUID.randomUUID(), Set.of(managedLayerId));
    var viewedBox = watchbox(UUID.randomUUID(), Set.of(viewedLayerId));
    var hiddenBox = watchbox(UUID.randomUUID(), Set.of(hiddenBoxLayerId));
    Set<UUID> union = Set.of(managedLayerId, viewedLayerId, hiddenBoxLayerId);
    when(layerAccess.rolesForLayers(union, caller))
        .thenReturn(
            Map.of(
                managedLayerId, WatchboxLayerRole.OWNER,
                viewedLayerId, WatchboxLayerRole.MEMBER));
    var access = new WatchboxLayerAccessPolicy(layerAccess).contextFor(union, caller);
    var managedStatus = statusRecord(sourceId);
    var viewedStatus = statusRecord(UUID.randomUUID());
    when(repository.findSourceStatusesForWatchboxes(
            Map.of(managedBox.id(), 7L, viewedBox.id(), 7L)))
        .thenReturn(
            Map.of(
                managedBox.id(), List.of(managedStatus),
                viewedBox.id(), List.of(viewedStatus)));
    // The source-name lookup unions only manager-visible rows' source ids.
    when(dataSourceRepository.findAll(
            new DataSourceReadFilter(
                null, Set.of(sourceId), RecordVisibility.ALL, null, null, null)))
        .thenReturn(List.of(source(sourceId, "cot-source")));

    var responses =
        assembler.assembleVisibleAll(
            List.of(managedBox, viewedBox, hiddenBox), access, DetectionLiveness.RUNNING);

    assertThat(responses)
        .extracting(WatchboxResponse::id)
        .containsExactly(managedBox.id(), viewedBox.id());
    assertThat(responses.get(0).capabilities()).isEqualTo(new WatchboxCapabilities(true, true));
    assertThat(responses.get(0).sourceStatuses())
        .singleElement()
        .satisfies(row -> assertThat(row.sourceName()).isEqualTo("cot-source"));
    assertThat(responses.get(1).capabilities()).isEqualTo(new WatchboxCapabilities(false, false));
    assertThat(responses.get(1).sourceStatuses()).isEmpty();
    verify(repository, never()).findSourceStatuses(any(), anyLong());
    verify(dataSourceRepository, times(1)).findAll(any(DataSourceReadFilter.class));
  }

  @Test
  void assembleVisibleAll_whenNothingVisible_readsNoStatuses() {
    var caller = new WatchboxCaller(userId, false);
    when(layerAccess.rolesForLayers(Set.of(layerId), caller)).thenReturn(Map.of());
    var access = new WatchboxLayerAccessPolicy(layerAccess).contextFor(Set.of(layerId), caller);

    assertThat(
            assembler.assembleVisibleAll(List.of(config(true)), access, DetectionLiveness.RUNNING))
        .isEmpty();

    verifyNoInteractions(repository);
  }

  private WatchboxConfig watchbox(UUID id, Set<UUID> layerIds) {
    return new WatchboxConfig(
        id,
        "watch-" + id,
        null,
        true,
        WatchboxDirection.BOTH,
        square(),
        WatchboxFilter.empty(),
        layerIds,
        7L,
        Instant.parse("2026-06-26T01:00:00Z"),
        Instant.parse("2026-06-26T01:00:00Z"));
  }

  private static WatchboxSourceStatusRecord statusRecord(UUID sourceId) {
    return new WatchboxSourceStatusRecord(
        sourceId,
        WatchboxAlertingStatus.DEGRADED,
        WatchboxStatusReason.WATCHBOX_MAPPER_OBJECT_ID_NOT_CONFIGURED,
        Instant.parse("2026-06-26T01:00:00Z"),
        1L,
        10L,
        Instant.parse("2026-06-26T01:01:00Z"));
  }

  private WatchboxConfig config(boolean enabled) {
    return config(enabled, Set.of(layerId));
  }

  private WatchboxConfig config(boolean enabled, Set<UUID> layerIds) {
    return new WatchboxConfig(
        watchboxId,
        "watch",
        null,
        enabled,
        WatchboxDirection.BOTH,
        square(),
        WatchboxFilter.empty(),
        layerIds,
        7L,
        Instant.parse("2026-06-26T01:00:00Z"),
        Instant.parse("2026-06-26T01:00:00Z"));
  }

  private static SocketDataSource source(UUID sourceId, String name) {
    return new SocketDataSource(
        sourceId,
        name,
        "tenant",
        new ConnectionSpec(
            new TcpConfig("127.0.0.1", 8087, null, null, null, null, new NoSecurity())),
        null,
        null,
        null,
        AccessMode.READ,
        true,
        null,
        null,
        null,
        Instant.now(),
        null,
        Instant.now(),
        null,
        null,
        true);
  }

  private static Polygon square() {
    return new Polygon(
        java.util.List.of(
            new LngLatAlt(-100.2, 39.8),
            new LngLatAlt(-99.8, 39.8),
            new LngLatAlt(-99.8, 40.2),
            new LngLatAlt(-100.2, 40.2),
            new LngLatAlt(-100.2, 39.8)));
  }
}
