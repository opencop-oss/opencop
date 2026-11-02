// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.technical.simulation;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Parameters controlling the synthetic history window, event cadence, entity count, and movement.
 * The endpoint applies non-null request values over {@link #defaults()}.
 */
public record HistoricalSimulationParameters(
    UUID sourceId,
    Instant start,
    Duration timeframe,
    Duration chunkDuration,
    Duration tickDuration,
    int uniquePoints,
    boolean randomSeeded,
    long seed,
    double moveMetersPerTick,
    double turnJitterDeg) {

  /** Source identifier used when a request does not provide one. */
  public static final UUID DEFAULT_SOURCE_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000000");

  /**
   * Returns a 26-hour window starting 25 hours before now, with one-hour chunks, one-minute ticks,
   * 5,000 entities, and a fixed seed.
   */
  public static HistoricalSimulationParameters defaults() {
    return new HistoricalSimulationParameters(
        DEFAULT_SOURCE_ID,
        Instant.now().minus(Duration.ofHours(25)),
        Duration.ofHours(26),
        Duration.ofHours(1),
        Duration.ofMinutes(1),
        5_000,
        true,
        42L,
        1.0,
        3.0);
  }

  /** Apply non-null overrides to the defaults. Used by the endpoint to coalesce request fields. */
  public HistoricalSimulationParameters withOverrides(
      UUID sourceId,
      Instant start,
      Duration timeframe,
      Duration chunkDuration,
      Duration tickDuration,
      Integer uniquePoints,
      Boolean randomSeeded,
      Long seed,
      Double moveMetersPerTick,
      Double turnJitterDeg) {
    return new HistoricalSimulationParameters(
        sourceId != null ? sourceId : this.sourceId,
        start != null ? start : this.start,
        timeframe != null ? timeframe : this.timeframe,
        chunkDuration != null ? chunkDuration : this.chunkDuration,
        tickDuration != null ? tickDuration : this.tickDuration,
        uniquePoints != null ? uniquePoints : this.uniquePoints,
        randomSeeded != null ? randomSeeded : this.randomSeeded,
        seed != null ? seed : this.seed,
        moveMetersPerTick != null ? moveMetersPerTick : this.moveMetersPerTick,
        turnJitterDeg != null ? turnJitterDeg : this.turnJitterDeg);
  }
}
