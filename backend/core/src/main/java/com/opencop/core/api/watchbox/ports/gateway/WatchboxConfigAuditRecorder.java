// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.watchbox.ports.gateway;

import com.opencop.core.api.watchbox.business.model.WatchboxConfig;
import com.opencop.core.api.watchbox.ports.usecase.WatchboxCaller;
import com.opencop.core.sdk.model.watchbox.WatchboxResetReason;
import java.util.List;

public interface WatchboxConfigAuditRecorder {

  void recordCreate(WatchboxConfig created, WatchboxCaller actor);

  void recordPatch(
      WatchboxConfig before,
      WatchboxConfig after,
      List<WatchboxResetReason> resetReasons,
      WatchboxCaller actor);

  void recordArchive(WatchboxConfig before, WatchboxCaller actor);
}
