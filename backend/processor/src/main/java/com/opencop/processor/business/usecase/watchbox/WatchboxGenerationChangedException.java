// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.usecase.watchbox;

/** Signals that a cached runtime target no longer belongs to the current tracking generation. */
public final class WatchboxGenerationChangedException extends RuntimeException {

  WatchboxGenerationChangedException() {
    super("Watchbox tracking generation changed during event processing");
  }
}
