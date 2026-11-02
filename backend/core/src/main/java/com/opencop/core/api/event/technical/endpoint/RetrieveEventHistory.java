// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.technical.endpoint;

import com.opencop.core.api.event.business.model.EventHistoryResponse;
import com.opencop.core.api.event.ports.business.usecase.RetrieveEventHistoryHandler;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RetrieveEventHistory {

  private final RetrieveEventHistoryHandler usecase;

  RetrieveEventHistory(final RetrieveEventHistoryHandler usecase) {
    this.usecase = usecase;
  }

  @GetMapping(
      value = "/v1/events/{sourceId}/{objectId}/history",
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<EventHistoryResponse> getHistory(
      @AuthenticationPrincipal OpenCopPrincipal authentication,
      @PathVariable UUID sourceId,
      @PathVariable String objectId,
      @RequestParam Instant from,
      @RequestParam Instant to,
      @RequestParam(defaultValue = "1000") int maxPoints,
      @RequestParam(defaultValue = "false") boolean asLine) {
    var response =
        this.usecase.getHistory(authentication, sourceId, objectId, from, to, maxPoints, asLine);
    return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(response);
  }
}
