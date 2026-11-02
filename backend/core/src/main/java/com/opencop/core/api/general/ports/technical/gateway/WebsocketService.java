// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.technical.gateway;

import com.opencop.core.sdk.model.notification.OpencopNotification;
import java.util.List;

public interface WebsocketService {

  void notify(OpencopNotification opencopNotification);

  void notifyAll(List<OpencopNotification> opencopNotification);
}
