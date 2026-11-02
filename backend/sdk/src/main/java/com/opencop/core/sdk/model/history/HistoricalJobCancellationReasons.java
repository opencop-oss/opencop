// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.history;

import java.util.Objects;
import java.util.UUID;

public final class HistoricalJobCancellationReasons {
  public static final String SUPERSEDED_BY_REQUEST_PREFIX = "Superseded by request ";

  private HistoricalJobCancellationReasons() {}

  public static String supersededByRequest(UUID replacementRequestId) {
    return SUPERSEDED_BY_REQUEST_PREFIX
        + Objects.requireNonNull(replacementRequestId, "replacementRequestId");
  }
}
