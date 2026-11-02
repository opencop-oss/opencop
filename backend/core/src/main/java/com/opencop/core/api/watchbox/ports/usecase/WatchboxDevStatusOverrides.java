// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.watchbox.ports.usecase;

import com.opencop.core.sdk.model.watchbox.WatchboxResponse;
import com.opencop.core.sdk.model.watchbox.WatchboxStatusOverrideRequest;
import java.util.Optional;
import java.util.UUID;

public interface WatchboxDevStatusOverrides {

  WatchboxStatusOverrideRequest set(UUID watchboxId, WatchboxStatusOverrideRequest request);

  Optional<WatchboxStatusOverrideRequest> get(UUID watchboxId);

  void clear(UUID watchboxId);

  WatchboxResponse apply(WatchboxResponse response, boolean includeSourceStatuses);
}
