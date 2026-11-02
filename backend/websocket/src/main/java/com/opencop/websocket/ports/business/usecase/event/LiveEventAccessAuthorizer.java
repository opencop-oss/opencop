// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.ports.business.usecase.event;

import java.util.UUID;

public interface LiveEventAccessAuthorizer {

  boolean userCanSubscribe(UUID userId, UUID resourceId);
}
