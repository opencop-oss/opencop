// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.usecase.event;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.sdk.model.cache.GeoCacheHydrationJob;
import com.opencop.core.sdk.model.job.GeoCacheHydrationJobSubmission;
import com.opencop.core.sdk.model.job.JobIssuer;
import com.opencop.core.sdk.model.job.LayerCopyJobSubmission;
import com.opencop.processor.ports.technical.gateway.db.LayerCopyGateway;
import com.opencop.processor.ports.technical.gateway.job.JobDispatchGateway;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LayerCopyHandlerTest {

  private static final JobIssuer ISSUER = JobIssuer.system("layer-copy-reconciliation");

  private final LayerCopyGateway layerCopyGateway = mock(LayerCopyGateway.class);
  private final JobDispatchGateway jobDispatchGateway = mock(JobDispatchGateway.class);
  private final LayerCopyHandler handler =
      new LayerCopyHandler(layerCopyGateway, jobDispatchGateway);

  @Test
  void handle_copiesLayerAndDispatchesHydrationFollowUp() {
    UUID sourceLayerId = UUID.fromString("550e8400-e29b-41d4-a716-446655446021");
    UUID targetLayerId = UUID.fromString("550e8400-e29b-41d4-a716-446655446022");
    var job =
        new LayerCopyJobSubmission(
            UUID.fromString("550e8400-e29b-41d4-a716-446655446020"),
            ISSUER,
            sourceLayerId,
            targetLayerId,
            true,
            false);

    when(layerCopyGateway.targetHasFeatures(targetLayerId)).thenReturn(false);

    handler.handle(job);

    verify(layerCopyGateway).copyLayer(sourceLayerId, targetLayerId, true, false);
    verify(jobDispatchGateway)
        .publish(
            argThat(
                submission ->
                    submission instanceof GeoCacheHydrationJobSubmission hydrationJob
                        && ISSUER.equals(hydrationJob.issuer())
                        && targetLayerId.equals(hydrationJob.sourceId())
                        && GeoCacheHydrationJob.Mode.LATEST_ACTIVE_ONLY.equals(hydrationJob.mode())
                        && GeoCacheHydrationJob.Trigger.LAYER_CLONE.equals(hydrationJob.trigger())
                        && hydrationJob.jobId() != null));
  }

  @Test
  void handle_whenHydrationPublishFails_rethrowsSoJobCanBeRetried() {
    UUID sourceLayerId = UUID.fromString("550e8400-e29b-41d4-a716-446655446021");
    UUID targetLayerId = UUID.fromString("550e8400-e29b-41d4-a716-446655446022");
    var job =
        new LayerCopyJobSubmission(
            UUID.fromString("550e8400-e29b-41d4-a716-446655446020"),
            ISSUER,
            sourceLayerId,
            targetLayerId,
            true,
            false);

    when(layerCopyGateway.targetHasFeatures(targetLayerId)).thenReturn(false);
    doThrow(new IllegalStateException("publish failed"))
        .when(jobDispatchGateway)
        .publish(argThat(submission -> submission instanceof GeoCacheHydrationJobSubmission));

    assertThrows(IllegalStateException.class, () -> handler.handle(job));

    verify(layerCopyGateway).copyLayer(sourceLayerId, targetLayerId, true, false);
    verify(jobDispatchGateway)
        .publish(
            argThat(
                submission ->
                    submission instanceof GeoCacheHydrationJobSubmission hydrationJob
                        && ISSUER.equals(hydrationJob.issuer())
                        && targetLayerId.equals(hydrationJob.sourceId())
                        && GeoCacheHydrationJob.Trigger.LAYER_CLONE.equals(
                            hydrationJob.trigger())));
  }

  @Test
  void handle_whenTargetAlreadyHasFeatures_skipsCopyAndDispatchesHydrationFollowUp() {
    UUID sourceLayerId = UUID.fromString("550e8400-e29b-41d4-a716-446655446021");
    UUID targetLayerId = UUID.fromString("550e8400-e29b-41d4-a716-446655446022");
    var job =
        new LayerCopyJobSubmission(
            UUID.fromString("550e8400-e29b-41d4-a716-446655446020"),
            ISSUER,
            sourceLayerId,
            targetLayerId,
            true,
            false);

    when(layerCopyGateway.targetHasFeatures(targetLayerId)).thenReturn(true);

    handler.handle(job);

    verify(layerCopyGateway, never()).copyLayer(sourceLayerId, targetLayerId, true, false);
    verify(jobDispatchGateway)
        .publish(
            argThat(
                submission ->
                    submission instanceof GeoCacheHydrationJobSubmission hydrationJob
                        && ISSUER.equals(hydrationJob.issuer())
                        && targetLayerId.equals(hydrationJob.sourceId())
                        && GeoCacheHydrationJob.Trigger.LAYER_CLONE.equals(
                            hydrationJob.trigger())));
  }
}
