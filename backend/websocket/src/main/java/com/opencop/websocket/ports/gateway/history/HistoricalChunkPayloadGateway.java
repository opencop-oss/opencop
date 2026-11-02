// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.ports.gateway.history;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface HistoricalChunkPayloadGateway {

  Optional<byte[]> find(String payloadKey);

  default Map<String, Optional<byte[]>> findAll(List<String> payloadKeys) {
    return payloadKeys.stream()
        .collect(
            java.util.stream.Collectors.toMap(
                key -> key, this::find, (left, right) -> right, java.util.LinkedHashMap::new));
  }
}
