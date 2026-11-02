// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.ports.business.usecase;

import com.opencop.core.api.event.business.model.BoundingBox;
import com.opencop.core.api.event.business.model.SourceHistogramResponse;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.time.Instant;
import java.util.UUID;

public interface RetrieveSourceHistogramHandler {

  SourceHistogramResponse getHistogram(
      OpenCopPrincipal principal, UUID sourceId, Instant from, Instant to, BoundingBox bbox);
}
