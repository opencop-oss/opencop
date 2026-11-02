// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.ports.gateway.websocket;

/**
 * Outcome of an awaited send attempt to a user's active websocket sessions.
 *
 * <p>NO_SESSION — the user had no local session subscribed to the target destination. DELIVERED —
 * at least one local session accepted the frame successfully. Failures are surfaced as exceptional
 * CompletableFuture completions, never as a third value here.
 */
public enum SendAttemptResult {
  NO_SESSION,
  DELIVERED
}
