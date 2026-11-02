// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.model.history.chunk;

public class HistoricalChunkPayloadSizeException extends IllegalStateException {

  private static final long serialVersionUID = 1L;

  private final String chunkKey;

  public HistoricalChunkPayloadSizeException(String chunkKey, String message) {
    super(message);
    this.chunkKey = chunkKey;
  }

  public String chunkKey() {
    return chunkKey;
  }
}
