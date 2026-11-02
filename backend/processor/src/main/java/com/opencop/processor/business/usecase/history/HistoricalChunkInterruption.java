// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.usecase.history;

final class HistoricalChunkInterruption {

  private static final String INTERRUPTED_MESSAGE =
      "Historical job interrupted during chunk execution";

  private HistoricalChunkInterruption() {}

  static void ensureNotInterrupted() {
    if (Thread.currentThread().isInterrupted()) {
      throw new IllegalStateException(INTERRUPTED_MESSAGE);
    }
  }

  static String interruptedMessage() {
    return INTERRUPTED_MESSAGE;
  }
}
