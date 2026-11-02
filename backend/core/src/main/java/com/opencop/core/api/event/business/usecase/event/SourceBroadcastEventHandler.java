// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.business.usecase.event;

import static com.opencop.core.api.event.ports.business.usecase.EventExceptionCodes.INTERNAL_ERROR;

import com.opencop.core.api.event.business.policy.BroadcastEventValidation;
import com.opencop.core.api.event.business.policy.SourceBroadcastPolicy;
import com.opencop.core.api.event.ports.business.usecase.EventBroadcaster;
import com.opencop.core.api.general.business.policy.ResourceLifecycleGuard;
import com.opencop.core.api.general.ports.gateway.job.JobPublisher;
import com.opencop.core.sdk.model.job.GhostEvictionJobSubmission;
import com.opencop.core.sdk.model.job.JobIssuer;
import com.opencop.core.sdk.model.provenance.FeatureProvenance.FeatureSourceKind;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.model.opencop.OpenCopProperties;
import com.opencop.core.sdk.technical.model.opencop.parser.OpenCopPropertiesParser;
import java.util.UUID;
import org.geojson.Feature;
import org.springframework.http.HttpStatus;

public class SourceBroadcastEventHandler implements EventBroadcaster<String> {

  private static final String PATCH_OPERATION = "source broadcast event patch";
  private static final String DELETE_OPERATION = "source broadcast event delete";

  private final BroadcastEventPublisher publisher;
  private final SourceBroadcastPolicy authorizationPolicy;
  private final ResourceLifecycleGuard resourceLifecycleGuard;
  private final JobPublisher jobPublisher;

  public SourceBroadcastEventHandler(
      final BroadcastEventPublisher publisher,
      final SourceBroadcastPolicy authorizationPolicy,
      final ResourceLifecycleGuard resourceLifecycleGuard,
      final JobPublisher jobPublisher) {
    this.publisher = publisher;
    this.authorizationPolicy = authorizationPolicy;
    this.resourceLifecycleGuard = resourceLifecycleGuard;
    this.jobPublisher = jobPublisher;
  }

  @Override
  public Feature create(UUID userId, UUID sourceId, Feature event) {
    throw new UnsupportedOperationException(
        "Broadcast create is not supported on data sources; use the layer endpoint");
  }

  @Override
  public Feature patch(UUID userId, UUID sourceId, String objectId, Feature event) {
    OpenCopProperties properties = publisher.requirePatchProperties(event);
    BroadcastEventValidation.assertGeometryValid(event.getGeometry());
    BroadcastEventValidation.assertSourceMatching(sourceId, "target source", properties);
    publisher.assertObjectIdMatching(objectId, properties);
    resourceLifecycleGuard.assertEventResourceActive(sourceId, PATCH_OPERATION);
    publisher.getLatestFeature(sourceId, objectId);
    authorizationPolicy.assertSourceMutator(userId, sourceId);

    return publisher.publish(
        sourceId,
        objectId,
        publisher.hydrateProperties(userId, objectId, properties),
        event.getGeometry(),
        FeatureSourceKind.DATA_SOURCE);
  }

  @Override
  public void delete(UUID userId, UUID sourceId, String objectId) {
    resourceLifecycleGuard.assertEventResourceActive(sourceId, DELETE_OPERATION);
    authorizationPolicy.assertSourceMutator(userId, sourceId);

    Feature latestFeature;
    try {
      latestFeature = publisher.getLatestFeature(sourceId, objectId);
    } catch (GlobalOpenCopException e) {
      if (HttpStatus.NOT_FOUND.equals(e.getHttpStatus())) {
        jobPublisher.publishNow(
            GhostEvictionJobSubmission.create(
                JobIssuer.system("ghost-eviction"), sourceId, objectId));
        return;
      }
      throw e;
    }

    OpenCopProperties properties = OpenCopPropertiesParser.readFromFeature(latestFeature);
    if (properties == null) {
      throw new GlobalOpenCopException(
          INTERNAL_ERROR.code(), "Could not parse properties", HttpStatus.INTERNAL_SERVER_ERROR);
    }
    BroadcastEventValidation.assertSourceMatching(sourceId, "target source", properties);
    publisher.assertObjectIdMatching(objectId, properties);

    publisher.markStaleAndPublish(
        sourceId, objectId, latestFeature, properties, FeatureSourceKind.DATA_SOURCE);
  }
}
