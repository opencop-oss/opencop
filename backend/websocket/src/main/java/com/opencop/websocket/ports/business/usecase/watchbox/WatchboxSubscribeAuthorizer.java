// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.ports.business.usecase.watchbox;

import java.util.UUID;

public interface WatchboxSubscribeAuthorizer {

  /**
   * Returns an authoritative completed authorization decision. A {@code true} or {@code false}
   * result means access was determined; inability to query or evaluate that access must propagate
   * as an exception rather than be converted to a denial.
   */
  boolean userCanSubscribe(UUID userId, UUID watchboxId);
}
