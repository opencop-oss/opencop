// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.technical.endpoint;

import com.opencop.core.api.event.business.model.BoundingBox;
import com.opencop.core.api.event.business.model.SourceHistogramResponse;
import com.opencop.core.api.event.ports.business.usecase.RetrieveSourceHistogramHandler;
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
public class RetrieveSourceHistogram {

  private final RetrieveSourceHistogramHandler usecase;

  RetrieveSourceHistogram(final RetrieveSourceHistogramHandler usecase) {
    this.usecase = usecase;
  }

  @GetMapping(
      value = "/v1/events/{sourceId}/histogram",
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<SourceHistogramResponse> getHistogram(
      @AuthenticationPrincipal OpenCopPrincipal authentication,
      @PathVariable UUID sourceId,
      @RequestParam Instant from,
      @RequestParam Instant to,
      @RequestParam(required = false) String bbox) {
    var response =
        this.usecase.getHistogram(authentication, sourceId, from, to, BoundingBox.parse(bbox));
    return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(response);
  }
}
