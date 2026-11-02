// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.business.usecase.event;

import static com.opencop.core.api.event.ports.business.usecase.EventExceptionCodes.INTERNAL_ERROR;
import static com.opencop.core.api.event.ports.business.usecase.EventExceptionCodes.MISSING_INFORMATION;

import com.opencop.core.api.event.business.policy.BroadcastEventValidation;
import com.opencop.core.api.event.business.policy.LayerBroadcastPolicy;
import com.opencop.core.api.event.ports.business.usecase.EventBroadcaster;
import com.opencop.core.api.event.ports.business.usecase.OpencopEventException;
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

public class LayerBroadcastEventHandler implements EventBroadcaster<UUID> {

  private static final String CREATE_OPERATION = "broadcast event create";
  private static final String PATCH_OPERATION = "broadcast event patch";
  private static final String DELETE_OPERATION = "broadcast event delete";

  private final BroadcastEventPublisher publisher;
  private final LayerBroadcastPolicy authorizationPolicy;
  private final ResourceLifecycleGuard resourceLifecycleGuard;
  private final JobPublisher jobPublisher;

  public LayerBroadcastEventHandler(
      final BroadcastEventPublisher publisher,
      final LayerBroadcastPolicy authorizationPolicy,
      final ResourceLifecycleGuard resourceLifecycleGuard,
      final JobPublisher jobPublisher) {
    this.publisher = publisher;
    this.authorizationPolicy = authorizationPolicy;
    this.resourceLifecycleGuard = resourceLifecycleGuard;
    this.jobPublisher = jobPublisher;
  }

  @Override
  public Feature create(UUID userId, UUID targetLayer, Feature event) {
    OpenCopProperties properties = requireCreateProperties(event);
    BroadcastEventValidation.assertGeometryValid(event.getGeometry());
    BroadcastEventValidation.assertSourceMatching(targetLayer, "target layer", properties);
    resourceLifecycleGuard.assertLayerActive(targetLayer, CREATE_OPERATION);
    authorizationPolicy.assertLayerEditor(userId, targetLayer);

    String objectId = UUID.randomUUID().toString();
    return publisher.publish(
        targetLayer,
        objectId,
        publisher.hydrateProperties(userId, objectId, properties),
        event.getGeometry(),
        FeatureSourceKind.LAYER);
  }

  @Override
  public Feature patch(UUID userId, UUID targetLayer, UUID objectId, Feature event) {
    String objectIdStr = objectId.toString();
    OpenCopProperties properties = publisher.requirePatchProperties(event);
    BroadcastEventValidation.assertGeometryValid(event.getGeometry());
    BroadcastEventValidation.assertSourceMatching(targetLayer, "target layer", properties);
    publisher.assertObjectIdMatching(objectIdStr, properties);
    resourceLifecycleGuard.assertEventResourceActive(targetLayer, PATCH_OPERATION);
    publisher.getLatestFeature(targetLayer, objectIdStr);
    authorizationPolicy.assertLayerEditor(userId, targetLayer);

    return publisher.publish(
        targetLayer,
        objectIdStr,
        publisher.hydrateProperties(userId, objectIdStr, properties),
        event.getGeometry(),
        FeatureSourceKind.LAYER);
  }

  @Override
  public void delete(UUID userId, UUID targetLayer, UUID objectId) {
    String objectIdStr = objectId.toString();
    resourceLifecycleGuard.assertEventResourceActive(targetLayer, DELETE_OPERATION);
    authorizationPolicy.assertLayerEditor(userId, targetLayer);

    Feature latestFeature;
    try {
      latestFeature = publisher.getLatestFeature(targetLayer, objectIdStr);
    } catch (GlobalOpenCopException e) {
      if (HttpStatus.NOT_FOUND.equals(e.getHttpStatus())) {
        jobPublisher.publishNow(
            GhostEvictionJobSubmission.create(
                JobIssuer.system("ghost-eviction"), targetLayer, objectIdStr));
        return;
      }
      throw e;
    }

    OpenCopProperties properties = OpenCopPropertiesParser.readFromFeature(latestFeature);
    if (properties == null) {
      throw new GlobalOpenCopException(
          INTERNAL_ERROR.code(), "Could not parse properties", HttpStatus.INTERNAL_SERVER_ERROR);
    }
    BroadcastEventValidation.assertSourceMatching(targetLayer, "target layer", properties);
    publisher.assertObjectIdMatching(objectIdStr, properties);

    publisher.markStaleAndPublish(
        targetLayer, objectIdStr, latestFeature, properties, FeatureSourceKind.LAYER);
  }

  private OpenCopProperties requireCreateProperties(Feature event) {
    OpenCopProperties properties = OpenCopPropertiesParser.readFromFeature(event);
    if (properties == null || properties.getSource() == null) {
      throw new OpencopEventException(MISSING_INFORMATION, HttpStatus.BAD_REQUEST);
    }
    BroadcastEventValidation.assertTimestampzValid(properties, true);
    return properties;
  }
}
