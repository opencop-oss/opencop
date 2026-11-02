// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.gateway.socket;

/** Internal raw-connector lifecycle state, including its thread and open session ownership. */
sealed interface ConnectorPhase
    permits ConnectorPhase.New,
        ConnectorPhase.Connecting,
        ConnectorPhase.Running,
        ConnectorPhase.Stopped,
        ConnectorPhase.Disconnected,
        ConnectorPhase.Incompatible,
        ConnectorPhase.Failed {

  record New() implements ConnectorPhase {}

  record Connecting(Thread thread) implements ConnectorPhase {}

  record Running(Thread thread, Session session) implements ConnectorPhase {}

  record Stopped() implements ConnectorPhase {}

  record Disconnected() implements ConnectorPhase {}

  record Incompatible(Throwable cause) implements ConnectorPhase {}

  record Failed(Throwable cause) implements ConnectorPhase {}
}
