// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.ports.business.usecase.history;

import com.opencop.core.sdk.model.history.HistoryEvent;

public interface HistoryEventHandler {

  void handle(HistoryEvent event);
}
