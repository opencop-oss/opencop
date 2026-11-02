// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.ports.business.usecase.event;

public interface EventHandler<I> {

  void handle(String source, I input, String destination);
}
