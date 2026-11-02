// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history.model.chunk;

public enum HistoricalChunkStateStatus {
  PLANNED,
  COMPUTING,
  CACHED,
  AVAILABLE_FOR_DELIVERY,
  RECOMPUTE_NEEDED,
  DELIVERED,
  FAILED,
  CANCELLED
}
