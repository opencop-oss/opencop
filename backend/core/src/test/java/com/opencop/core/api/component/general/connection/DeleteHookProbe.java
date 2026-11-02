// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.connection;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/** Records which fixture extensions released a deleted connection, and as whom. */
final class DeleteHookProbe {

  /** One delete hook's call. */
  record Call(String extensionId, UUID sourceId, UUID actingUserId) {}

  private final List<Call> calls = new CopyOnWriteArrayList<>();

  void record(Call call) {
    calls.add(call);
  }

  List<Call> calls() {
    return List.copyOf(calls);
  }

  void reset() {
    calls.clear();
  }
}
