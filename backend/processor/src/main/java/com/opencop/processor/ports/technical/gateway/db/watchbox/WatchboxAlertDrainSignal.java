// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.ports.technical.gateway.db.watchbox;

/**
 * Post-commit nudge that new alert delivery work exists in the outbox. It is required for normal
 * low-latency live delivery because it wakes the local drainer immediately. Outbox durability and
 * eventual recovery do not depend on the nudge: startup draining and fallback polling recover a
 * missed or crashed nudge.
 */
@FunctionalInterface
public interface WatchboxAlertDrainSignal {

  void requestDrain();
}
