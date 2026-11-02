// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.gateway.socket;

import com.opencop.api.connect.ports.gateway.socket.connector.SocketConnector;
import com.opencop.core.sdk.business.model.integration.connection.behavior.ConnectionBehaviorPlan;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/** Applies compiled connection behavior while owning the shared timing and execution resources. */
public final class ConnectionBehaviorRuntime {

  private final ScheduledThreadPoolExecutor scheduler;
  private final ExecutorService behaviorExecutor;
  private final Clock clock;

  public ConnectionBehaviorRuntime(
      ScheduledThreadPoolExecutor scheduler, ExecutorService behaviorExecutor, Clock clock) {
    this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    this.behaviorExecutor = Objects.requireNonNull(behaviorExecutor, "behaviorExecutor");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /**
   * Wraps an integration-backed connector, even when its plan is empty, so a later change of its
   * effective settings can swap the plan without reconnecting.
   *
   * @param effectiveSettings the effective settings the plan was compiled from
   */
  public SocketConnector decorate(
      SocketConnector raw, UUID sourceId, ConnectionBehaviorPlan plan, Object effectiveSettings) {
    Objects.requireNonNull(raw, "raw");
    Objects.requireNonNull(sourceId, "sourceId");
    return new ConnectionBehaviorSocketConnector(
        raw, sourceId, plan, effectiveSettings, scheduler, behaviorExecutor, clock);
  }
}
