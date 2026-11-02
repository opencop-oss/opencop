// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.component.support;

import com.opencop.api.connect.business.connector.ConnectorListener;
import com.opencop.core.sdk.ports.gateway.socket.ConnectorState;
import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Test listener that records connector callbacks for explicit assertions. */
public final class RecordingConnectorListener implements ConnectorListener {

  private final LinkedBlockingQueue<ConnectorState> states = new LinkedBlockingQueue<>();
  private final LinkedBlockingQueue<byte[]> messages = new LinkedBlockingQueue<>();
  private final LinkedBlockingQueue<Throwable> errors = new LinkedBlockingQueue<>();
  private final LinkedBlockingQueue<Throwable> restartRequests = new LinkedBlockingQueue<>();

  @Override
  public void onMessage(UUID sourceId, byte[] data) {
    messages.add(Arrays.copyOf(data, data.length));
  }

  @Override
  public void onStateChange(UUID sourceId, ConnectorState state) {
    states.add(state);
  }

  @Override
  public void onError(UUID sourceId, Throwable error) {
    errors.add(error);
  }

  @Override
  public void onRestartRequested(UUID sourceId, Throwable reason) {
    restartRequests.add(reason);
  }

  public ConnectorState awaitState(Duration timeout) throws InterruptedException {
    var state = states.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
    if (state == null) {
      throw new AssertionError("Timed out waiting for connector state within " + timeout);
    }
    return state;
  }

  public void awaitStateEventually(ConnectorState expected, Duration timeout)
      throws InterruptedException {
    var deadline = System.nanoTime() + timeout.toNanos();
    var seen = new java.util.ArrayList<ConnectorState>();
    while (true) {
      var remaining = deadline - System.nanoTime();
      if (remaining <= 0) {
        throw new AssertionError(
            "Timed out waiting for connector state "
                + expected
                + " within "
                + timeout
                + "; observed: "
                + seen);
      }
      var state = states.poll(remaining, TimeUnit.NANOSECONDS);
      if (state == null) {
        throw new AssertionError(
            "Timed out waiting for connector state "
                + expected
                + " within "
                + timeout
                + "; observed: "
                + seen);
      }
      seen.add(state);
      if (state == expected) {
        return;
      }
    }
  }

  public byte[] awaitMessage(Duration timeout) throws InterruptedException {
    var message = messages.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
    if (message == null) {
      throw new AssertionError("Timed out waiting for connector message within " + timeout);
    }
    return message;
  }

  public Throwable awaitError(Duration timeout) throws InterruptedException {
    var error = errors.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
    if (error == null) {
      throw new AssertionError("Timed out waiting for connector error within " + timeout);
    }
    return error;
  }

  public Throwable awaitRestartRequest(Duration timeout) throws InterruptedException {
    var reason = restartRequests.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
    if (reason == null) {
      throw new AssertionError("Timed out waiting for connector restart request within " + timeout);
    }
    return reason;
  }

  public int pendingMessageCount() {
    return messages.size();
  }

  public int pendingStateCount() {
    return states.size();
  }

  public int pendingErrorCount() {
    return errors.size();
  }

  public int pendingRestartRequestCount() {
    return restartRequests.size();
  }
}
