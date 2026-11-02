// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.technical.simulation;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Development-only, admin-only endpoint for generating synthetic historical events through {@link
 * HistoricalSimulationService}.
 *
 * <p>Each request can override the source, time window, chunk and tick durations, entity count,
 * random seed, and movement settings. Null fields retain the values from {@link
 * HistoricalSimulationParameters#defaults()}.
 */
@RestController
@ConditionalOnProperty(name = "opencop.dev.mode", havingValue = "true", matchIfMissing = false)
@PreAuthorize("hasRole('admin')")
public class HistoricalSimulationEndpoint {

  private final HistoricalSimulationService service;

  public HistoricalSimulationEndpoint(HistoricalSimulationService service) {
    this.service = service;
  }

  @PostMapping(
      value = "/v1/history/test-data/generate",
      produces = MediaType.APPLICATION_JSON_VALUE,
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<HistoricalSimulationResponse> generate(@RequestBody Request request) {
    HistoricalSimulationParameters parameters =
        HistoricalSimulationParameters.defaults()
            .withOverrides(
                request.sourceId(),
                request.windowStart(),
                request.windowDuration(),
                request.chunkDuration(),
                request.tickDuration(),
                request.uniquePoints(),
                request.randomSeeded(),
                request.seed(),
                request.moveMetersPerTick(),
                request.turnJitterDeg());
    HistoricalSimulationResponse response = service.generate(parameters);
    return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(response);
  }

  /**
   * All fields are optional. Duration fields use the ISO-8601 format, such as {@code PT24H}, and
   * null values retain their defaults.
   */
  public record Request(
      UUID sourceId,
      Instant windowStart,
      Duration windowDuration,
      Duration chunkDuration,
      Duration tickDuration,
      Integer uniquePoints,
      Boolean randomSeeded,
      Long seed,
      Double moveMetersPerTick,
      Double turnJitterDeg) {}
}
