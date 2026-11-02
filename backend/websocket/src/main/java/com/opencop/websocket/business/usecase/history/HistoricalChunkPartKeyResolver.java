// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.business.usecase.history;

public class HistoricalChunkPartKeyResolver {

  public String payloadPartKey(String payloadKey, int partIndex) {
    return partIndex == 0 ? payloadKey : payloadKey + ":part:" + partIndex;
  }
}
