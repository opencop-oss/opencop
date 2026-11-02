// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.connection.behavior;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ConnectionBehaviorPlanTest {

  @Test
  void defensivelyCopiesExecutionValuesAndReportsWhetherItIsEmpty() {
    ConnectionBehaviorPlan.MessageAction message =
        new ConnectionBehaviorPlan.MessageAction("presence", (_, _) -> new byte[] {1});
    var onConnect = new ArrayList<>(List.of(message));
    var recurring =
        new ArrayList<>(
            List.of(new ConnectionBehaviorPlan.RecurringAction(message, Duration.ofSeconds(20))));

    var plan = new ConnectionBehaviorPlan(onConnect, recurring, Optional.empty(), Optional.empty());
    onConnect.clear();
    recurring.clear();

    assertThat(plan.onConnect()).containsExactly(message).isUnmodifiable();
    assertThat(plan.recurring()).hasSize(1).isUnmodifiable();
    assertThat(plan.isEmpty()).isFalse();
    assertThat(ConnectionBehaviorPlan.empty().isEmpty()).isTrue();
  }

  @Test
  void rejectsBlankNamesAndMissingFactories() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ConnectionBehaviorPlan.MessageAction(" ", (_, _) -> new byte[0]))
        .withMessage("name must not be blank");
    assertThatNullPointerException()
        .isThrownBy(() -> new ConnectionBehaviorPlan.MessageAction("message", null))
        .withMessage("factory");
  }

  @Test
  void rejectsNonPositiveRecurringAndIdleDurations() {
    var message = new ConnectionBehaviorPlan.MessageAction("message", (_, _) -> new byte[0]);

    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ConnectionBehaviorPlan.RecurringAction(message, Duration.ZERO))
        .withMessage("delay must be positive");
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> new ConnectionBehaviorPlan.IdleMessageAction(message, Duration.ofSeconds(-1)))
        .withMessage("threshold must be positive");
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new ConnectionBehaviorPlan(
                    List.of(), List.of(), Optional.empty(), Optional.of(Duration.ZERO)))
        .withMessage("reconnectAfterIdle must be positive");
  }
}
