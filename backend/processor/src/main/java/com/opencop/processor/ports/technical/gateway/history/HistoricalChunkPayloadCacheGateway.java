// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.ports.technical.gateway.history;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface HistoricalChunkPayloadCacheGateway {

  /**
   * Returns the remaining TTL for each requested payload key.
   *
   * <ul>
   *   <li>If a key is absent from the result map, it does not exist in the cache.
   *   <li>If a key maps to {@code Optional.empty()}, it exists with no expiry.
   *   <li>If a key maps to {@code Optional.of(duration)}, that is the remaining TTL.
   * </ul>
   */
  Map<String, Optional<Duration>> remainingPayloadTtl(Set<String> payloadKeys);

  void put(String payloadKey, byte[] payloadBytes, Duration ttl);

  Optional<byte[]> find(String payloadKey);

  Map<String, Optional<byte[]>> findAll(List<String> payloadKeys);
}
