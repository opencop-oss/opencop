// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.cache.redis;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;
import java.time.Duration;

/** Shared Lettuce client options for Redis connections used as best-effort transport. */
public final class RedisClientOptionsFactory {

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
  private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(5);
  private static final Duration KEEPALIVE_IDLE = Duration.ofSeconds(30);
  private static final Duration KEEPALIVE_INTERVAL = Duration.ofSeconds(10);
  private static final int KEEPALIVE_COUNT = 3;

  private RedisClientOptionsFactory() {}

  public static void applyHardening(ClientOptions.Builder builder) {
    var keepAlive =
        SocketOptions.KeepAliveOptions.builder()
            .enable()
            .idle(KEEPALIVE_IDLE)
            .interval(KEEPALIVE_INTERVAL)
            .count(KEEPALIVE_COUNT)
            .build();
    var socketOptions =
        SocketOptions.builder()
            .connectTimeout(CONNECT_TIMEOUT)
            .keepAlive(keepAlive)
            .tcpNoDelay(true)
            .build();

    builder
        .autoReconnect(true)
        .pingBeforeActivateConnection(true)
        .socketOptions(socketOptions)
        .timeoutOptions(TimeoutOptions.enabled(COMMAND_TIMEOUT));
  }
}
