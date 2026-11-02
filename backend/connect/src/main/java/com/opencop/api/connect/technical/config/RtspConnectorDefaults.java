// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.config;

import java.time.Duration;
import java.util.Objects;

public record RtspConnectorDefaults(
    Duration connectTimeout, Duration readTimeout, boolean preferTcp) {

  public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(30);
  public static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(60);
  public static final boolean DEFAULT_PREFER_TCP = true;

  public RtspConnectorDefaults {
    connectTimeout = requirePositive(connectTimeout, "connectTimeout");
    readTimeout = requirePositive(readTimeout, "readTimeout");
  }

  public static RtspConnectorDefaults defaults() {
    return new RtspConnectorDefaults(
        DEFAULT_CONNECT_TIMEOUT, DEFAULT_READ_TIMEOUT, DEFAULT_PREFER_TCP);
  }

  public int connectTimeoutMicros() {
    return toMicros(connectTimeout, "connectTimeout");
  }

  public int readTimeoutMicros() {
    return toMicros(readTimeout, "readTimeout");
  }

  private static Duration requirePositive(Duration value, String name) {
    var duration = Objects.requireNonNull(value, name);
    if (duration.isZero() || duration.isNegative()) {
      throw new IllegalArgumentException("RTSP " + name + " must be positive");
    }
    return duration;
  }

  private static int toMicros(Duration duration, String name) {
    var nanos = duration.toNanos();
    var micros = Math.max(1L, nanos / 1_000L);
    if (micros > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("RTSP " + name + " is too large");
    }
    return (int) micros;
  }
}
