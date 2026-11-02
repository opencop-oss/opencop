// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.ports.gateway.history;

import java.util.UUID;

public interface HistoricalTracksAdmissionGateway {
  int requestCancellationForActiveJobs(UUID requestedBy, UUID replacementRequestId);
}
