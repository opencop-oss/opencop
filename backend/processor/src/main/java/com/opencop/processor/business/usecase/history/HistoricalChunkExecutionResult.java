// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.usecase.history;

enum HistoricalChunkExecutionResult {
  REUSED_FROM_CACHE,
  COMPUTED,
  FAILED
}
