// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.model.watchbox;

import java.util.Objects;
import java.util.UUID;

/**
 * A detection the database accepted as novel, with the storage id the insert returned. The outbox
 * carries the id so the live alert can name the retained row.
 */
public record AcceptedDetection(UUID id, WatchboxTransition transition) {
  public AcceptedDetection {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(transition, "transition");
  }
}
