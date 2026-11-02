// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.watchbox.ports.usecase;

import com.opencop.core.sdk.model.watchbox.WatchboxCreateRequest;
import com.opencop.core.sdk.model.watchbox.WatchboxPatchRequest;
import com.opencop.core.sdk.model.watchbox.WatchboxResponse;
import java.util.List;
import java.util.UUID;

public interface ManageWatchboxes {

  WatchboxResponse create(WatchboxCreateRequest request, WatchboxCaller caller);

  List<WatchboxResponse> list(WatchboxCaller caller);

  WatchboxResponse get(UUID watchboxId, WatchboxCaller caller);

  WatchboxResponse patch(UUID watchboxId, WatchboxPatchRequest request, WatchboxCaller caller);

  void delete(UUID watchboxId, WatchboxCaller caller);
}
