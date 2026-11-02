// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.media;

import com.opencop.core.api.general.business.usecase.media.AuthorizeStreamPlaybackUsecase;
import com.opencop.core.api.general.business.usecase.media.StreamAuthorizationDecision;
import com.opencop.core.api.general.business.usecase.media.StreamAuthorizationReason;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StreamAuthorizationEndpoint {

  private final AuthorizeStreamPlaybackUsecase authorizeStreamPlayback;
  private final StreamAuthorizationTelemetry telemetry;

  public StreamAuthorizationEndpoint(
      AuthorizeStreamPlaybackUsecase authorizeStreamPlayback,
      StreamAuthorizationTelemetry telemetry) {
    this.authorizeStreamPlayback = authorizeStreamPlayback;
    this.telemetry = telemetry;
  }

  @GetMapping("/streams/authorize")
  public ResponseEntity<Void> authorize(
      @AuthenticationPrincipal OpenCopPrincipal authentication,
      @RequestHeader(name = "X-Forwarded-Uri", required = false) String forwardedUri,
      @RequestHeader(name = "X-Forwarded-Method", required = false) String forwardedMethod) {
    if (authentication == null) {
      telemetry.recordDenial(StreamAuthorizationReason.UNAUTHENTICATED);
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    var decision = authorizeWithTelemetry(authentication, forwardedUri, forwardedMethod);
    return decision.allowed()
        ? ResponseEntity.noContent().build()
        : ResponseEntity.status(HttpStatus.FORBIDDEN).build();
  }

  private StreamAuthorizationDecision authorizeWithTelemetry(
      OpenCopPrincipal authentication, String forwardedUri, String forwardedMethod) {
    try {
      var decision =
          authorizeStreamPlayback.authorize(authentication, forwardedUri, forwardedMethod);
      telemetry.recordDecision(decision);
      return decision;
    } catch (RuntimeException e) {
      telemetry.recordDenial(StreamAuthorizationReason.UNEXPECTED_ERROR);
      throw e;
    }
  }
}
