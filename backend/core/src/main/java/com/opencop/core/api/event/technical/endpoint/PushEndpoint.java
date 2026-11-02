// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.technical.endpoint;

import com.opencop.core.api.general.ports.usecase.connection.push.PushFeatures;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.UUID;
import org.geojson.FeatureCollection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PushEndpoint {

  private final PushFeatures pushFeatures;

  public PushEndpoint(PushFeatures pushFeatures) {
    this.pushFeatures = pushFeatures;
  }

  @PostMapping("/v1/connections/{sourceId}/events/push")
  public ResponseEntity<Void> push(
      @AuthenticationPrincipal OpenCopPrincipal authentication,
      @PathVariable UUID sourceId,
      @RequestBody FeatureCollection features) {
    final UUID userId = authentication.internalUserId();
    pushFeatures.execute(sourceId, userId, features);
    return ResponseEntity.status(HttpStatus.ACCEPTED).build();
  }
}
