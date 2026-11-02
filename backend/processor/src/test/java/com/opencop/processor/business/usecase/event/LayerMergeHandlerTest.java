// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.usecase.event;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.sdk.model.job.JobIssuer;
import com.opencop.core.sdk.model.job.LayerMergeJobSubmission;
import com.opencop.core.sdk.model.layermerge.LayerMergeOperation;
import com.opencop.core.sdk.model.layermerge.LayerMergeOperationPhase;
import com.opencop.processor.ports.technical.gateway.cache.GeoCacheGateway;
import com.opencop.processor.ports.technical.gateway.db.GeoCacheHydrationGateway;
import com.opencop.processor.ports.technical.gateway.db.GeoCacheHydrationGateway.HydrationSnapshotStats;
import com.opencop.processor.ports.technical.gateway.db.LayerMergeGateway;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LayerMergeHandlerTest {

  private static final UUID FIRST_SOURCE_LAYER_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655448001");
  private static final UUID SECOND_SOURCE_LAYER_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655448002");

  @Test
  void dispatchRoutesLayerMergeSubmissionToHandlerStub() {
    var handler =
        spy(
            new LayerMergeHandler(
                pendingMergeGateway(),
                hydrationGatewayWithSuccessfulSnapshot(),
                mock(GeoCacheGateway.class)));
    var registry = new JobHandlerRegistry(List.of(handler));
    var job = layerMergeJob();

    assertDoesNotThrow(() -> registry.dispatch(job));

    verify(handler).handle(job);
  }

  @Test
  void jobTypeMatchesLayerMergeSubmissionClass() {
    var handler =
        new LayerMergeHandler(
            pendingMergeGateway(),
            mock(GeoCacheHydrationGateway.class),
            mock(GeoCacheGateway.class));

    assertEquals(LayerMergeJobSubmission.class, handler.jobType());
  }

  @Test
  void handleMergesTargetLayerFromOrderedSources() {
    var mergeGateway = pendingMergeGateway();
    var hydrationGateway = hydrationGatewayWithSuccessfulSnapshot();
    var cacheGateway = mock(GeoCacheGateway.class);
    var handler = new LayerMergeHandler(mergeGateway, hydrationGateway, cacheGateway);
    var job = layerMergeJob();

    handler.handle(job);

    verify(mergeGateway)
        .mergeLayersAndAdvanceOperation(
            job.sourceLayerIds(), job.targetLayerId(), LayerMergeOperationPhase.MATERIALIZED);
  }

  @Test
  void handle_whenOperationIsFinalized_retriesSourceEvictionOnly() {
    var mergeGateway = pendingMergeGateway();
    var hydrationGateway = hydrationGatewayWithSuccessfulSnapshot();
    var cacheGateway = mock(GeoCacheGateway.class);
    var handler = new LayerMergeHandler(mergeGateway, hydrationGateway, cacheGateway);
    var job = layerMergeJob();
    when(mergeGateway.findMergeOperation(job.targetLayerId()))
        .thenReturn(Optional.of(layerMergeOperation(LayerMergeOperationPhase.FINALIZED)));

    assertDoesNotThrow(() -> handler.handle(job));

    verify(mergeGateway, never()).mergeLayersAndAdvanceOperation(any(), any(), any());
    verify(mergeGateway, never()).finalizeMergeFailure(any(), any());
    verify(mergeGateway, never())
        .finalizeMergeSuccessAndAdvanceOperation(any(), any(), any(), any());
    verify(hydrationGateway, never()).hydrateSnapshot(any(), eq(1000), any());
    verify(cacheGateway).clearSource(FIRST_SOURCE_LAYER_ID);
    verify(cacheGateway).clearSource(SECOND_SOURCE_LAYER_ID);
  }

  @Test
  void handle_whenOperationIsFinalizedAndSourceEvictionFails_throwsForReplay() {
    var mergeGateway = pendingMergeGateway();
    var hydrationGateway = hydrationGatewayWithSuccessfulSnapshot();
    var cacheGateway = mock(GeoCacheGateway.class);
    var failure = new IllegalStateException("redis eviction failed");
    doThrow(failure).when(cacheGateway).clearSource(SECOND_SOURCE_LAYER_ID);
    var handler = new LayerMergeHandler(mergeGateway, hydrationGateway, cacheGateway);
    var job = layerMergeJob();
    when(mergeGateway.findMergeOperation(job.targetLayerId()))
        .thenReturn(Optional.of(layerMergeOperation(LayerMergeOperationPhase.FINALIZED)));

    var thrown = assertThrows(IllegalStateException.class, () -> handler.handle(job));

    assertEquals(failure, thrown);
    verify(mergeGateway, never()).mergeLayersAndAdvanceOperation(any(), any(), any());
    verify(mergeGateway, never()).finalizeMergeFailure(any(), any());
    verify(mergeGateway, never())
        .finalizeMergeSuccessAndAdvanceOperation(any(), any(), any(), any());
    verify(hydrationGateway, never()).hydrateSnapshot(any(), eq(1000), any());
    verify(cacheGateway).clearSource(FIRST_SOURCE_LAYER_ID);
    verify(cacheGateway).clearSource(SECOND_SOURCE_LAYER_ID);
  }

  @Test
  void handle_whenMergeOperationIsMissing_throwsInvariantFailureWithoutMutation() {
    var mergeGateway = pendingMergeGateway();
    var hydrationGateway = hydrationGatewayWithSuccessfulSnapshot();
    var cacheGateway = mock(GeoCacheGateway.class);
    var handler = new LayerMergeHandler(mergeGateway, hydrationGateway, cacheGateway);
    var job = layerMergeJob();
    when(mergeGateway.findMergeOperation(job.targetLayerId())).thenReturn(Optional.empty());

    assertThrows(IllegalStateException.class, () -> handler.handle(job));

    verify(mergeGateway, never()).mergeLayersAndAdvanceOperation(any(), any(), any());
    verify(mergeGateway, never()).finalizeMergeFailure(any(), any());
    verify(mergeGateway, never())
        .finalizeMergeSuccessAndAdvanceOperation(any(), any(), any(), any());
    verify(mergeGateway, never()).advanceMergeOperation(any(), any(), any());
    verify(mergeGateway, never()).markMergeOperationFailed(any(), any(), any());
    verify(hydrationGateway, never()).hydrateSnapshot(any(), eq(1000), any());
    verify(cacheGateway, never()).clearSource(any());
  }

  @Test
  void
      handle_whenMergeAndCacheFinalizationSucceed_archivesSourcesAfterTargetHydrationAndEviction() {
    var mergeGateway = pendingMergeGateway();
    var hydrationGateway = hydrationGatewayWithSuccessfulSnapshot();
    var cacheGateway = mock(GeoCacheGateway.class);
    var handler = new LayerMergeHandler(mergeGateway, hydrationGateway, cacheGateway);
    var job = layerMergeJob();

    handler.handle(job);

    var inOrder = inOrder(mergeGateway, hydrationGateway, cacheGateway);
    inOrder
        .verify(mergeGateway)
        .mergeLayersAndAdvanceOperation(
            job.sourceLayerIds(), job.targetLayerId(), LayerMergeOperationPhase.MATERIALIZED);
    inOrder.verify(hydrationGateway).hydrateSnapshot(eq(job.targetLayerId()), eq(1000), any());
    inOrder
        .verify(mergeGateway)
        .advanceMergeOperation(
            job.targetLayerId(),
            LayerMergeOperationPhase.MATERIALIZED,
            LayerMergeOperationPhase.HYDRATED);
    inOrder
        .verify(mergeGateway)
        .finalizeMergeSuccessAndAdvanceOperation(
            job.targetLayerId(),
            job.sourceLayerIds(),
            UUID.fromString(job.issuer().id()),
            LayerMergeOperationPhase.FINALIZED);
    inOrder.verify(cacheGateway).clearSource(FIRST_SOURCE_LAYER_ID);
    inOrder.verify(cacheGateway).clearSource(SECOND_SOURCE_LAYER_ID);
  }

  @Test
  void handle_whenOperationIsFailed_skipsWithoutMutation() {
    var mergeGateway = pendingMergeGateway();
    var hydrationGateway = hydrationGatewayWithSuccessfulSnapshot();
    var cacheGateway = mock(GeoCacheGateway.class);
    var handler = new LayerMergeHandler(mergeGateway, hydrationGateway, cacheGateway);
    var job = layerMergeJob();
    when(mergeGateway.findMergeOperation(job.targetLayerId()))
        .thenReturn(Optional.of(layerMergeOperation(LayerMergeOperationPhase.FAILED)));

    assertDoesNotThrow(() -> handler.handle(job));

    verify(mergeGateway, never()).mergeLayersAndAdvanceOperation(any(), any(), any());
    verify(mergeGateway, never()).finalizeMergeFailure(any(), any());
    verify(mergeGateway, never())
        .finalizeMergeSuccessAndAdvanceOperation(any(), any(), any(), any());
    verify(mergeGateway, never()).advanceMergeOperation(any(), any(), any());
    verify(hydrationGateway, never()).hydrateSnapshot(any(), eq(1000), any());
    verify(cacheGateway, never()).clearSource(any());
  }

  @Test
  void handle_whenOperationSourceOrderConflicts_marksFailedAndSkipsMutation() {
    var mergeGateway = pendingMergeGateway();
    var hydrationGateway = hydrationGatewayWithSuccessfulSnapshot();
    var cacheGateway = mock(GeoCacheGateway.class);
    var handler = new LayerMergeHandler(mergeGateway, hydrationGateway, cacheGateway);
    var job = layerMergeJob();
    when(mergeGateway.findMergeOperation(job.targetLayerId()))
        .thenReturn(
            Optional.of(
                new LayerMergeOperation(
                    job.targetLayerId(),
                    job.jobId(),
                    List.of(SECOND_SOURCE_LAYER_ID, FIRST_SOURCE_LAYER_ID),
                    LayerMergeOperationPhase.PENDING,
                    null)));

    assertThrows(IllegalStateException.class, () -> handler.handle(job));

    verify(mergeGateway)
        .markMergeOperationFailed(
            job.targetLayerId(), LayerMergeOperationPhase.PENDING, "OPERATION_IDENTITY_CONFLICT");
    verify(mergeGateway, never()).mergeLayersAndAdvanceOperation(any(), any(), any());
    verify(mergeGateway, never()).finalizeMergeFailure(any(), any());
    verify(mergeGateway, never())
        .finalizeMergeSuccessAndAdvanceOperation(any(), any(), any(), any());
    verify(mergeGateway, never()).advanceMergeOperation(any(), any(), any());
    verify(hydrationGateway, never()).hydrateSnapshot(any(), eq(1000), any());
    verify(cacheGateway, never()).clearSource(any());
  }

  @Test
  void
      handle_whenAdvancedOrTerminalOperationSourceOrderConflicts_preservesAcceptedOperationState() {
    var phases =
        List.of(
            LayerMergeOperationPhase.MATERIALIZED,
            LayerMergeOperationPhase.HYDRATED,
            LayerMergeOperationPhase.FINALIZED,
            LayerMergeOperationPhase.FAILED);

    for (LayerMergeOperationPhase phase : phases) {
      var mergeGateway = pendingMergeGateway();
      var hydrationGateway = hydrationGatewayWithSuccessfulSnapshot();
      var cacheGateway = mock(GeoCacheGateway.class);
      var handler = new LayerMergeHandler(mergeGateway, hydrationGateway, cacheGateway);
      var job = layerMergeJob();
      when(mergeGateway.findMergeOperation(job.targetLayerId()))
          .thenReturn(
              Optional.of(
                  new LayerMergeOperation(
                      job.targetLayerId(),
                      job.jobId(),
                      List.of(SECOND_SOURCE_LAYER_ID, FIRST_SOURCE_LAYER_ID),
                      phase,
                      null)));

      assertThrows(IllegalStateException.class, () -> handler.handle(job));

      verify(mergeGateway, never()).markMergeOperationFailed(any(), any(), any());
      verify(mergeGateway, never()).mergeLayersAndAdvanceOperation(any(), any(), any());
      verify(mergeGateway, never()).finalizeMergeFailure(any(), any());
      verify(mergeGateway, never())
          .finalizeMergeSuccessAndAdvanceOperation(any(), any(), any(), any());
      verify(mergeGateway, never()).advanceMergeOperation(any(), any(), any());
      verify(hydrationGateway, never()).hydrateSnapshot(any(), eq(1000), any());
      verify(cacheGateway, never()).clearSource(any());
    }
  }

  @Test
  void handle_whenOperationIsMaterialized_skipsMaterializationAndHydrates() {
    var mergeGateway = pendingMergeGateway();
    var hydrationGateway = hydrationGatewayWithSuccessfulSnapshot();
    var cacheGateway = mock(GeoCacheGateway.class);
    var handler = new LayerMergeHandler(mergeGateway, hydrationGateway, cacheGateway);
    var job = layerMergeJob();
    when(mergeGateway.findMergeOperation(job.targetLayerId()))
        .thenReturn(Optional.of(layerMergeOperation(LayerMergeOperationPhase.MATERIALIZED)));

    handler.handle(job);

    verify(mergeGateway, never()).mergeLayersAndAdvanceOperation(any(), any(), any());
    verify(hydrationGateway).hydrateSnapshot(eq(job.targetLayerId()), eq(1000), any());
    verify(mergeGateway)
        .advanceMergeOperation(
            job.targetLayerId(),
            LayerMergeOperationPhase.MATERIALIZED,
            LayerMergeOperationPhase.HYDRATED);
    verify(mergeGateway)
        .finalizeMergeSuccessAndAdvanceOperation(
            job.targetLayerId(),
            job.sourceLayerIds(),
            UUID.fromString(job.issuer().id()),
            LayerMergeOperationPhase.FINALIZED);
  }

  @Test
  void handle_whenOperationIsHydrated_skipsMaterializationAndHydrationThenFinalizes() {
    var mergeGateway = pendingMergeGateway();
    var hydrationGateway = hydrationGatewayWithSuccessfulSnapshot();
    var cacheGateway = mock(GeoCacheGateway.class);
    var handler = new LayerMergeHandler(mergeGateway, hydrationGateway, cacheGateway);
    var job = layerMergeJob();
    when(mergeGateway.findMergeOperation(job.targetLayerId()))
        .thenReturn(Optional.of(layerMergeOperation(LayerMergeOperationPhase.HYDRATED)));

    handler.handle(job);

    verify(mergeGateway, never()).mergeLayersAndAdvanceOperation(any(), any(), any());
    verify(hydrationGateway, never()).hydrateSnapshot(any(), eq(1000), any());
    verify(mergeGateway)
        .finalizeMergeSuccessAndAdvanceOperation(
            job.targetLayerId(),
            job.sourceLayerIds(),
            UUID.fromString(job.issuer().id()),
            LayerMergeOperationPhase.FINALIZED);
  }

  @Test
  void handle_whenSourceEvictionFailsAfterSuccess_keepsMergeFinalizedAndThrowsForReplay() {
    var mergeGateway = pendingMergeGateway();
    var hydrationGateway = hydrationGatewayWithSuccessfulSnapshot();
    var cacheGateway = mock(GeoCacheGateway.class);
    var failure = new IllegalStateException("redis eviction failed");
    doThrow(failure).when(cacheGateway).clearSource(SECOND_SOURCE_LAYER_ID);
    var handler = new LayerMergeHandler(mergeGateway, hydrationGateway, cacheGateway);
    var job = layerMergeJob();

    var thrown = assertThrows(IllegalStateException.class, () -> handler.handle(job));

    assertEquals(failure, thrown.getCause());
    var inOrder = inOrder(mergeGateway, hydrationGateway, cacheGateway);
    inOrder
        .verify(mergeGateway)
        .mergeLayersAndAdvanceOperation(
            job.sourceLayerIds(), job.targetLayerId(), LayerMergeOperationPhase.MATERIALIZED);
    inOrder.verify(hydrationGateway).hydrateSnapshot(eq(job.targetLayerId()), eq(1000), any());
    inOrder
        .verify(mergeGateway)
        .advanceMergeOperation(
            job.targetLayerId(),
            LayerMergeOperationPhase.MATERIALIZED,
            LayerMergeOperationPhase.HYDRATED);
    inOrder
        .verify(mergeGateway)
        .finalizeMergeSuccessAndAdvanceOperation(
            job.targetLayerId(),
            job.sourceLayerIds(),
            UUID.fromString(job.issuer().id()),
            LayerMergeOperationPhase.FINALIZED);
    inOrder.verify(cacheGateway).clearSource(FIRST_SOURCE_LAYER_ID);
    inOrder.verify(cacheGateway).clearSource(SECOND_SOURCE_LAYER_ID);
    verify(mergeGateway, never()).finalizeMergeFailure(job.targetLayerId(), job.sourceLayerIds());
  }

  @Test
  void handle_whenFinalizeSuccessThrowsAfterHydration_leavesHydratedForReplay() {
    var mergeGateway = pendingMergeGateway();
    var hydrationGateway = hydrationGatewayWithSuccessfulSnapshot();
    var cacheGateway = mock(GeoCacheGateway.class);
    var failure = new IllegalStateException("finalization acknowledgement failed");
    var handler = new LayerMergeHandler(mergeGateway, hydrationGateway, cacheGateway);
    var job = layerMergeJob();
    doThrow(failure)
        .when(mergeGateway)
        .finalizeMergeSuccessAndAdvanceOperation(
            job.targetLayerId(),
            job.sourceLayerIds(),
            UUID.fromString(job.issuer().id()),
            LayerMergeOperationPhase.FINALIZED);

    var thrown = assertThrows(IllegalStateException.class, () -> handler.handle(job));

    assertEquals(failure, thrown);
    var inOrder = inOrder(mergeGateway, hydrationGateway, cacheGateway);
    inOrder
        .verify(mergeGateway)
        .mergeLayersAndAdvanceOperation(
            job.sourceLayerIds(), job.targetLayerId(), LayerMergeOperationPhase.MATERIALIZED);
    inOrder.verify(hydrationGateway).hydrateSnapshot(eq(job.targetLayerId()), eq(1000), any());
    inOrder
        .verify(mergeGateway)
        .advanceMergeOperation(
            job.targetLayerId(),
            LayerMergeOperationPhase.MATERIALIZED,
            LayerMergeOperationPhase.HYDRATED);
    inOrder
        .verify(mergeGateway)
        .finalizeMergeSuccessAndAdvanceOperation(
            job.targetLayerId(),
            job.sourceLayerIds(),
            UUID.fromString(job.issuer().id()),
            LayerMergeOperationPhase.FINALIZED);
    verify(mergeGateway, never()).finalizeMergeFailure(job.targetLayerId(), job.sourceLayerIds());
    verify(cacheGateway, never()).clearSource(any());
  }

  @Test
  void handle_whenTargetHydrationFails_leavesMaterializedForReplay() {
    var mergeGateway = pendingMergeGateway();
    var hydrationGateway = mock(GeoCacheHydrationGateway.class);
    var cacheGateway = mock(GeoCacheGateway.class);
    var failure = new IllegalStateException("target hydration failed");
    doThrow(failure).when(hydrationGateway).hydrateSnapshot(any(), eq(1000), any());
    var handler = new LayerMergeHandler(mergeGateway, hydrationGateway, cacheGateway);
    var job = layerMergeJob();

    var thrown = assertThrows(IllegalStateException.class, () -> handler.handle(job));

    assertEquals(failure, thrown);
    var inOrder = inOrder(mergeGateway, hydrationGateway, cacheGateway);
    inOrder
        .verify(mergeGateway)
        .mergeLayersAndAdvanceOperation(
            job.sourceLayerIds(), job.targetLayerId(), LayerMergeOperationPhase.MATERIALIZED);
    inOrder.verify(hydrationGateway).hydrateSnapshot(eq(job.targetLayerId()), eq(1000), any());
    verify(mergeGateway, never()).finalizeMergeFailure(job.targetLayerId(), job.sourceLayerIds());
    verify(cacheGateway, never()).clearSource(job.targetLayerId());
    verify(mergeGateway, never())
        .finalizeMergeSuccessAndAdvanceOperation(
            job.targetLayerId(),
            job.sourceLayerIds(),
            UUID.fromString(job.issuer().id()),
            LayerMergeOperationPhase.FINALIZED);
    verify(cacheGateway, never()).clearSource(FIRST_SOURCE_LAYER_ID);
    verify(cacheGateway, never()).clearSource(SECOND_SOURCE_LAYER_ID);
  }

  @Test
  void handle_whenMaterializationFails_marksOperationAndTargetFailedThenClearsTargetCache() {
    var mergeGateway = pendingMergeGateway();
    var hydrationGateway = hydrationGatewayWithSuccessfulSnapshot();
    var cacheGateway = mock(GeoCacheGateway.class);
    var failure = new IllegalStateException("materialization failed");
    doThrow(failure).when(mergeGateway).mergeLayersAndAdvanceOperation(any(), any(), any());
    var handler = new LayerMergeHandler(mergeGateway, hydrationGateway, cacheGateway);
    var job = layerMergeJob();

    var thrown = assertThrows(IllegalStateException.class, () -> handler.handle(job));

    assertEquals(failure, thrown.getCause());
    verify(mergeGateway)
        .finalizeMergeFailureAndSetOperationPhase(
            job.targetLayerId(),
            job.sourceLayerIds(),
            LayerMergeOperationPhase.FAILED,
            "MATERIALIZATION_FAILED");
    verify(cacheGateway).clearSource(job.targetLayerId());
    verify(mergeGateway, never()).advanceMergeOperation(any(), any(), any());
    verify(hydrationGateway, never()).hydrateSnapshot(any(), eq(1000), any());
  }

  private static LayerMergeJobSubmission layerMergeJob() {
    return new LayerMergeJobSubmission(
        UUID.fromString("550e8400-e29b-41d4-a716-446655448000"),
        JobIssuer.user(UUID.fromString("550e8400-e29b-41d4-a716-446655448099")),
        UUID.fromString("550e8400-e29b-41d4-a716-446655448003"),
        List.of(FIRST_SOURCE_LAYER_ID, SECOND_SOURCE_LAYER_ID));
  }

  private static LayerMergeOperation layerMergeOperation(LayerMergeOperationPhase phase) {
    var job = layerMergeJob();
    return new LayerMergeOperation(
        job.targetLayerId(), job.jobId(), job.sourceLayerIds(), phase, null);
  }

  private static LayerMergeGateway pendingMergeGateway() {
    var mergeGateway = mock(LayerMergeGateway.class);
    when(mergeGateway.isMergeTargetPending(any())).thenReturn(true);
    var job = layerMergeJob();
    when(mergeGateway.findMergeOperation(job.targetLayerId()))
        .thenReturn(Optional.of(layerMergeOperation(LayerMergeOperationPhase.PENDING)));
    return mergeGateway;
  }

  private static GeoCacheHydrationGateway hydrationGatewayWithSuccessfulSnapshot() {
    var hydrationGateway = mock(GeoCacheHydrationGateway.class);
    org.mockito.Mockito.when(hydrationGateway.hydrateSnapshot(any(), eq(1000), any()))
        .thenReturn(new HydrationSnapshotStats(1, 2, "last-object"));
    return hydrationGateway;
  }
}
