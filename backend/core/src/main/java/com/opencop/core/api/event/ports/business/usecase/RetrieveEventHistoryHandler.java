// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.ports.business.usecase;

import com.opencop.core.api.event.business.model.EventHistoryResponse;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.time.Instant;
import java.util.UUID;

public interface RetrieveEventHistoryHandler {

  EventHistoryResponse getHistory(
      OpenCopPrincipal principal,
      UUID source,
      String objectId,
      Instant from,
      Instant to,
      int maxPoints,
      boolean asLine);
}
