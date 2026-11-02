// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.ports.gateway.cache.geo;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Reads live geo payloads without exposing their storage layout. */
public interface GeoEventReader {
  List<byte[]> readObjects(String sourceId, List<String> objectIds);

  /** Progress counts index members, including members whose payload has expired. */
  Page readPage(String sourceId, long offset, int pageSize);

  record Page(int consumedMembers, List<byte[]> payloads) {

    public Page {
      if (consumedMembers < 0) {
        throw new IllegalArgumentException("consumedMembers must not be negative");
      }
      Objects.requireNonNull(payloads, "payloads");
      var copiedPayloads = new ArrayList<byte[]>(payloads.size());
      for (var payload : payloads) {
        copiedPayloads.add(Objects.requireNonNull(payload, "payloads must not contain null"));
      }
      if (copiedPayloads.size() > consumedMembers) {
        throw new IllegalArgumentException("payload count must not exceed consumedMembers");
      }
      payloads = List.copyOf(copiedPayloads);
    }
  }
}
