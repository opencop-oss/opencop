// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.ports.usecase.event;

import java.util.UUID;

public interface EventHandler<O> {

  void handle(UUID serverId, O rawInput);
}
