// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.technical.endpoint;

import com.opencop.core.api.event.ports.business.usecase.SubmitHistoricalTracksHandler;
import com.opencop.core.sdk.model.history.HistoricalTracksAck;
import com.opencop.core.sdk.model.history.HistoricalTracksRequest;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HistoricalTracksEndpoint {

  private final SubmitHistoricalTracksHandler usecase;

  public HistoricalTracksEndpoint(SubmitHistoricalTracksHandler usecase) {
    this.usecase = usecase;
  }

  @PostMapping(value = "/v1/history/tracks", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<HistoricalTracksAck> submit(
      @AuthenticationPrincipal OpenCopPrincipal authentication,
      @RequestBody HistoricalTracksRequest request) {
    var response = usecase.submit(authentication, request);
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .contentType(MediaType.APPLICATION_JSON)
        .body(response);
  }
}
