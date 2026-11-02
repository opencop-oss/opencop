// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.gateway.socket;

import com.opencop.api.connect.ports.gateway.socket.connector.SocketConnector;
import com.opencop.core.sdk.business.model.integration.connection.behavior.ConnectionBehaviorPlan.MessageAction;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class MessageActionExecutor {

  private static final Logger log = LoggerFactory.getLogger(MessageActionExecutor.class);

  private final SocketConnector raw;
  private final UUID sourceId;
  private final Clock clock;

  MessageActionExecutor(SocketConnector raw, UUID sourceId, Clock clock) {
    this.raw = Objects.requireNonNull(raw, "raw");
    this.sourceId = Objects.requireNonNull(sourceId, "sourceId");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  MessageAttemptOutcome attempt(MessageAction action) {
    try {
      raw.send(action.factory().create(sourceId, clock.instant()));
      return MessageAttemptOutcome.COMPLETED;
    } catch (InterruptedException _) {
      Thread.currentThread().interrupt();
      return MessageAttemptOutcome.INTERRUPTED;
    } catch (Exception exception) {
      if (Thread.currentThread().isInterrupted()) {
        return MessageAttemptOutcome.INTERRUPTED;
      }
      log.error(
          "Connection {} failed to send integration-control message {} ({})",
          sourceId,
          action.name(),
          exception.getClass().getSimpleName());
      return MessageAttemptOutcome.COMPLETED;
    }
  }
}
