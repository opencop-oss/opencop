// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.model.history.chunk;

import java.util.Objects;
import opencop.core.proto.history.HistoricalChunkProtos.HistoricalChunkPayload;

public record HistoricalChunkPayloadPart(
    String payloadKey,
    HistoricalChunkPayload payload,
    byte[] serializedPayload,
    int serializedSizeBytes,
    int partIndex,
    int partCount,
    boolean finalForChunk) {

  public HistoricalChunkPayloadPart {
    Objects.requireNonNull(payloadKey, "payloadKey");
    Objects.requireNonNull(payload, "payload");
    Objects.requireNonNull(serializedPayload, "serializedPayload");
  }
}
