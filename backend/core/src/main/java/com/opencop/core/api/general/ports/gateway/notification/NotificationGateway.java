// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.gateway.notification;

import java.util.UUID;

/**
 * Sends notifications to their recipients without waiting for delivery, one operation per kind of
 * notification.
 */
public interface NotificationGateway {

  /**
   * Tells a connection's owner they now hold the connection they created, which keeps the owner's
   * other sessions in step with it.
   */
  void notifyAddedOwner(UUID sourceId, UUID ownerId);
}
