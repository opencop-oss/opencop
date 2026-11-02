// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.component.support.assertion;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opencop.api.connect.component.support.RecordingConnectorListener;
import com.opencop.core.sdk.ports.gateway.socket.ConnectorState;
import java.time.Duration;

public final class ConnectorLifecycleAssertions {

  private ConnectorLifecycleAssertions() {}

  public static void assertState(
      RecordingConnectorListener listener, ConnectorState expected, Duration timeout)
      throws InterruptedException {
    assertEquals(expected, listener.awaitState(timeout));
  }

  public static void assertStateEventually(
      RecordingConnectorListener listener, ConnectorState expected, Duration timeout)
      throws InterruptedException {
    listener.awaitStateEventually(expected, timeout);
  }

  public static void assertMessage(
      RecordingConnectorListener listener, byte[] expected, Duration timeout)
      throws InterruptedException {
    assertArrayEquals(expected, listener.awaitMessage(timeout));
  }

  public static void assertNoBufferedMessages(RecordingConnectorListener listener) {
    assertEquals(0, listener.pendingMessageCount());
  }

  public static void assertNoBufferedStates(RecordingConnectorListener listener) {
    assertEquals(0, listener.pendingStateCount());
  }

  public static void assertNoBufferedErrors(RecordingConnectorListener listener) {
    assertEquals(0, listener.pendingErrorCount());
  }

  public static void assertAnyError(RecordingConnectorListener listener, Duration timeout)
      throws InterruptedException {
    assertTrue(listener.awaitError(timeout) != null);
  }
}
