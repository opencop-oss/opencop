// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.ports.business.usecase;

import com.opencop.core.sdk.model.history.HistoricalTracksAck;
import com.opencop.core.sdk.model.history.HistoricalTracksRequest;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;

public interface SubmitHistoricalTracksHandler {

  HistoricalTracksAck submit(OpenCopPrincipal principal, HistoricalTracksRequest request);
}
