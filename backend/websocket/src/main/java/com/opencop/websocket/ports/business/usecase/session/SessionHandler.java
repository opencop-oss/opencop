// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.ports.business.usecase.session;

public interface SessionHandler {

  void onConnect(String userId);

  void onDisconnect(String userId);
}
