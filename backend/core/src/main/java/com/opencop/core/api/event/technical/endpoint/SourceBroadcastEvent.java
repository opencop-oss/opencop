// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.technical.endpoint;

import com.opencop.core.api.event.business.usecase.event.SourceBroadcastEventHandler;
import com.opencop.core.api.event.ports.business.usecase.EventBroadcaster;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.UUID;
import org.geojson.Feature;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SourceBroadcastEvent {

  private final EventBroadcaster<String> service;

  SourceBroadcastEvent(final SourceBroadcastEventHandler service) {
    this.service = service;
  }

  @PatchMapping("/v1/events/broadcast/source/{sourceId}/{objectId}")
  public ResponseEntity<Feature> patchObject(
      @AuthenticationPrincipal OpenCopPrincipal authentication,
      @PathVariable UUID sourceId,
      @PathVariable String objectId,
      @RequestBody Feature feature) {
    var patchedFeature =
        this.service.patch(authentication.internalUserId(), sourceId, objectId, feature);
    return ResponseEntity.ok(patchedFeature);
  }

  @DeleteMapping("/v1/events/broadcast/source/{sourceId}/{objectId}")
  public ResponseEntity<Void> deleteObject(
      @AuthenticationPrincipal OpenCopPrincipal authentication,
      @PathVariable UUID sourceId,
      @PathVariable String objectId) {
    this.service.delete(authentication.internalUserId(), sourceId, objectId);
    return ResponseEntity.accepted().build();
  }
}
