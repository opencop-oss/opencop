// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.ports.technical.gateway.db.watchbox;

import com.opencop.processor.business.model.watchbox.WatchboxRuntimeSnapshot;

public interface WatchboxRuntimeConfigGateway {

  WatchboxRuntimeSnapshot load();

  /**
   * Reloads after the database rejects a snapshot's tracking generation.
   *
   * <p>Uncached adapters can load normally. A cache must not serve the rejected snapshot as
   * last-known-good if this reload fails.
   */
  default WatchboxRuntimeSnapshot reloadAfterGenerationChange(
      WatchboxRuntimeSnapshot rejectedSnapshot) {
    return load();
  }

  /**
   * Discards a snapshot rejected by the database without loading its replacement.
   *
   * <p>Called when the handler has exhausted its one in-process reevaluation and will propagate to
   * listener retry. Uncached adapters have nothing to discard.
   */
  default void invalidateAfterGenerationChange(WatchboxRuntimeSnapshot rejectedSnapshot) {}
}
