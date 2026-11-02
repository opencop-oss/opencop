// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.technical.endpoint;

import com.opencop.core.api.event.business.usecase.event.LayerBroadcastEventHandler;
import com.opencop.core.api.event.ports.business.usecase.EventBroadcaster;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.UUID;
import org.geojson.Feature;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BroadcastEvent {

  private final EventBroadcaster<UUID> service;

  BroadcastEvent(final LayerBroadcastEventHandler service) {
    this.service = service;
  }

  @PostMapping("/v1/events/broadcast/layer/{targetLayer}")
  public ResponseEntity<Feature> createObject(
      @AuthenticationPrincipal OpenCopPrincipal authentication,
      @PathVariable UUID targetLayer,
      @RequestBody Feature feature) {
    var createdFeature = this.service.create(authentication.internalUserId(), targetLayer, feature);
    return ResponseEntity.ok(createdFeature);
  }

  @PatchMapping("/v1/events/broadcast/layer/{targetLayer}/{objectId}")
  public ResponseEntity<Feature> patchObject(
      @AuthenticationPrincipal OpenCopPrincipal authentication,
      @PathVariable UUID targetLayer,
      @PathVariable UUID objectId,
      @RequestBody Feature feature) {
    var patchedFeature =
        this.service.patch(authentication.internalUserId(), targetLayer, objectId, feature);
    return ResponseEntity.ok(patchedFeature);
  }

  @DeleteMapping("/v1/events/broadcast/layer/{targetLayer}/{objectId}")
  public ResponseEntity<Void> deleteObject(
      @AuthenticationPrincipal OpenCopPrincipal authentication,
      @PathVariable UUID targetLayer,
      @PathVariable UUID objectId) {
    this.service.delete(authentication.internalUserId(), targetLayer, objectId);
    return ResponseEntity.accepted().build();
  }
}
