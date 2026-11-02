// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.watchbox.ports.usecase;

import com.opencop.core.api.watchbox.business.model.WatchboxDetection;
import com.opencop.core.api.watchbox.business.model.WatchboxDetectionDetail;
import com.opencop.core.api.watchbox.business.model.WatchboxDetectionFilter;
import com.opencop.core.api.watchbox.business.model.WatchboxDetectionPageRequest;
import com.opencop.core.sdk.technical.gateway.db.PageResponse;
import java.util.UUID;

public interface ReadWatchboxDetections {
  PageResponse<WatchboxDetection> list(
      WatchboxDetectionFilter filter, WatchboxDetectionPageRequest page, WatchboxCaller caller);

  WatchboxDetectionDetail get(UUID detectionId, WatchboxCaller caller);
}
