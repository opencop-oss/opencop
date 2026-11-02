// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.gateway.socket;

import static org.junit.jupiter.api.Assertions.*;

import com.opencop.api.connect.ports.gateway.socket.framing.FramingStrategy;
import com.opencop.core.sdk.ports.gateway.socket.ConnectorState;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Concurrency tests for TcpConnector's state machine.
 *
 * <p>These tests verify that the CAS-based state transitions handle concurrent access correctly.
 * They use CountDownLatch to coordinate threads and create race conditions.
 *
 * <p>Warning: These are stress tests, not formal verification. Future: look at JCStress or similar.
 *
 * <p>These tests are excluded from normal builds. To run them:
 *
 * <pre>
 * cd connect
 * mvn test -Dtest.excluded.groups=
 * </pre>
 */
@Tag("stress")
class TcpConnectorConcurrencyTest {

  private ExecutorService executor;

  @AfterEach
  void tearDown() {
    if (executor != null) {
      executor.shutdownNow();
    }
  }

  @Test
  void start_transitionsFromNewToConnecting() throws Exception {
    CountDownLatch sessionOpened = new CountDownLatch(1);
    Session blockingSession = createBlockingSession(sessionOpened);
    TcpConnector connector =
        new TcpConnector(UUID.randomUUID(), blockingFraming(), () -> blockingSession);

    assertEquals(ConnectorState.NEW, connector.state());

    connector.start();

    // Wait for thread to reach CONNECTING/CONNECTED
    assertTrue(sessionOpened.await(1, TimeUnit.SECONDS), "Session should open");
    ConnectorState state = connector.state();
    assertTrue(
        state == ConnectorState.CONNECTING || state == ConnectorState.CONNECTED,
        "Expected CONNECTING or CONNECTED, got " + state);

    connector.stop();
  }

  @Test
  void concurrentStart_onlyOneThreadWins() throws Exception {
    int numThreads = 10;
    executor = Executors.newFixedThreadPool(numThreads);
    AtomicInteger sessionOpenCount = new AtomicInteger(0);

    // SessionOpener that counts how many times it's called
    SessionOpener countingOpener =
        () -> {
          sessionOpenCount.incrementAndGet();
          return createBlockingSession(new CountDownLatch(1));
        };

    for (int iteration = 0; iteration < 50; iteration++) {
      sessionOpenCount.set(0);
      TcpConnector connector =
          new TcpConnector(UUID.randomUUID(), blockingFraming(), countingOpener);

      CountDownLatch startGate = new CountDownLatch(1);
      CountDownLatch allStarted = new CountDownLatch(numThreads);

      // All threads wait at gate, then call start() simultaneously
      for (int i = 0; i < numThreads; i++) {
        executor.execute(
            () -> {
              try {
                startGate.await();
                connector.start();
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              } finally {
                allStarted.countDown();
              }
            });
      }

      startGate.countDown(); // Release all threads
      assertTrue(allStarted.await(1, TimeUnit.SECONDS), "All threads should complete");

      // Give the winning thread time to open session
      Thread.sleep(50);

      // Only one thread should have successfully started
      assertEquals(
          1,
          sessionOpenCount.get(),
          "Iteration "
              + iteration
              + ": Expected exactly 1 session open, got "
              + sessionOpenCount.get());

      connector.stop();
      Thread.sleep(10); // Let connector thread exit
    }
  }

  @Test
  void stopDuringConnecting_transitionsToStopped() throws Exception {
    CountDownLatch sessionOpenStarted = new CountDownLatch(1);
    CountDownLatch allowSessionToComplete = new CountDownLatch(1);

    // SessionOpener that blocks until we release it
    SessionOpener slowOpener =
        () -> {
          sessionOpenStarted.countDown();
          allowSessionToComplete.await(); // Block here
          return createBlockingSession(new CountDownLatch(1));
        };

    TcpConnector connector = new TcpConnector(UUID.randomUUID(), blockingFraming(), slowOpener);

    connector.start();

    // Wait for connector to be in the middle of opening session
    assertTrue(sessionOpenStarted.await(1, TimeUnit.SECONDS));
    assertEquals(ConnectorState.CONNECTING, connector.state());

    // Stop while connecting
    boolean stopped = connector.stop();
    assertTrue(stopped, "stop() should return true when stopping from CONNECTING");

    // Release the blocked session opener (it will see we're stopped)
    allowSessionToComplete.countDown();

    // Wait for thread to finish
    Thread.sleep(100);

    assertEquals(ConnectorState.STOPPED, connector.state());
  }

  @Test
  void concurrentStartAndStop_reachesValidState() throws Exception {
    executor = Executors.newFixedThreadPool(2);

    // All these are valid outcomes of racing start() and stop()
    Set<ConnectorState> validEndStates =
        Set.of(
            ConnectorState.NEW, // stop() won before start() ran
            ConnectorState.STOPPED, // start() ran, then stop() succeeded
            ConnectorState.CONNECTING, // start() won, stop() not yet processed
            ConnectorState.CONNECTED // start() won and connected before we checked
            );

    for (int iteration = 0; iteration < 100; iteration++) {
      TcpConnector connector =
          new TcpConnector(
              UUID.randomUUID(),
              blockingFraming(),
              () -> createBlockingSession(new CountDownLatch(1)));

      CountDownLatch gate = new CountDownLatch(1);
      CountDownLatch done = new CountDownLatch(2);

      executor.execute(
          () -> {
            await(gate);
            connector.start();
            done.countDown();
          });

      executor.execute(
          () -> {
            await(gate);
            connector.stop();
            done.countDown();
          });

      gate.countDown();
      assertTrue(done.await(1, TimeUnit.SECONDS));

      // Small delay to let state settle
      Thread.sleep(10);

      ConnectorState state = connector.state();
      assertTrue(
          validEndStates.contains(state),
          "Iteration " + iteration + ": Invalid end state: " + state);

      // Clean up if still running
      connector.stop();
      Thread.sleep(10);
    }
  }

  @Test
  void stopDuringReadLoop_transitionsToStopped() throws Exception {
    CountDownLatch readLoopStarted = new CountDownLatch(1);
    CountDownLatch stopCalled = new CountDownLatch(1);

    // Framing that signals when read loop starts, then blocks
    FramingStrategy signalingFraming =
        input -> {
          readLoopStarted.countDown();
          try {
            stopCalled.await(); // Block until stop() is called
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          throw new IOException("Session closed");
        };

    TcpConnector connector =
        new TcpConnector(UUID.randomUUID(), signalingFraming, () -> createSimpleSession());

    connector.start();

    // Wait for connector to be in read loop
    assertTrue(readLoopStarted.await(1, TimeUnit.SECONDS));
    assertEquals(ConnectorState.CONNECTED, connector.state());

    // Stop while in read loop
    boolean stopped = connector.stop();
    assertTrue(stopped);
    stopCalled.countDown(); // Release the blocked read

    // Wait for thread to finish
    Thread.sleep(100);

    assertEquals(ConnectorState.STOPPED, connector.state());
  }

  // --- Helper methods ---

  private static void await(CountDownLatch latch) {
    try {
      latch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /** Creates a FramingStrategy that blocks forever (until interrupted). */
  private static FramingStrategy blockingFraming() {
    return input -> {
      while (!Thread.currentThread().isInterrupted()) {
        try {
          Thread.sleep(100);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new IOException("Interrupted");
        }
      }
      throw new IOException("Interrupted");
    };
  }

  /** Creates a Session that signals when opened, then provides blocking input. */
  private static Session createBlockingSession(CountDownLatch opened) {
    return new Session() {
      private final InputStream input =
          new InputStream() {
            @Override
            public int read() throws IOException {
              try {
                Thread.sleep(Long.MAX_VALUE);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted");
              }
              return -1;
            }
          };

      {
        opened.countDown();
      }

      @Override
      public InputStream input() {
        return input;
      }

      @Override
      public ByteArrayOutputStream output() {
        return new ByteArrayOutputStream();
      }

      @Override
      public void close() {
        // no-op
      }
    };
  }

  /** Creates a simple Session with empty streams. */
  private static Session createSimpleSession() {
    return new Session() {
      @Override
      public InputStream input() {
        return new ByteArrayInputStream(new byte[0]);
      }

      @Override
      public ByteArrayOutputStream output() {
        return new ByteArrayOutputStream();
      }

      @Override
      public void close() {
        // no-op
      }
    };
  }
}
