// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.ports.business.usecase.notification;

import com.opencop.core.sdk.model.notification.OpencopNotification;
import java.util.UUID;

public interface NotificationHandler {

  void notify(UUID userId, OpencopNotification opencopNotification);
}
