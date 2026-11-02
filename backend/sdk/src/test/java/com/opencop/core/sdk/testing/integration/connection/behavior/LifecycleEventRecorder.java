// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.testing.integration.connection.behavior;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/** Thread-safe recorder assigning a shared deterministic order to lifecycle test events. */
public final class LifecycleEventRecorder {

  private long nextOrder;
  private final List<Event> events = new CopyOnWriteArrayList<>();

  /** Records an event and returns its zero-based order within this recorder. */
  public synchronized long record(String name) {
    long order = nextOrder++;
    events.add(new Event(order, name));
    return order;
  }

  /** Returns an immutable snapshot of all recorded events. */
  public List<Event> events() {
    return List.copyOf(events);
  }

  /** One ordered lifecycle event. */
  public record Event(long order, String name) {

    public Event {
      if (order < 0) {
        throw new IllegalArgumentException("order must not be negative");
      }
      Objects.requireNonNull(name, "name");
    }
  }
}
