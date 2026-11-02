// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.gateway.socket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.api.connect.business.connector.ConnectorListener;
import com.opencop.core.sdk.ports.gateway.socket.ConnectorState;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class TcpConnectorTransportTest {

  @Test
  void opensReadsPublishesTransportLifecycleAndClosesTheSession() throws Exception {
    var sourceId = UUID.randomUUID();
    var closed = new AtomicBoolean();
    var firstRead = new AtomicBoolean(true);
    var session = session(new byte[] {1, 2, 3}, closed);
    var events = new CopyOnWriteArrayList<String>();
    var disconnected = new CountDownLatch(1);
    var connector =
        new TcpConnector(
            sourceId,
            input -> firstRead.getAndSet(false) ? input.readAllBytes() : null,
            () -> session);
    connector.addListener(
        new ConnectorListener() {
          @Override
          public void onStateChange(UUID eventSourceId, ConnectorState state) {
            events.add(state.name());
            if (state == ConnectorState.DISCONNECTED) {
              disconnected.countDown();
            }
          }

          @Override
          public void onMessage(UUID eventSourceId, byte[] data) {
            assertThat(eventSourceId).isEqualTo(sourceId);
            events.add("MESSAGE:" + List.of(data[0], data[1], data[2]));
          }
        });

    connector.start();

    assertThat(disconnected.await(1, TimeUnit.SECONDS)).isTrue();
    assertThat(events)
        .containsExactly("CONNECTING", "CONNECTED", "MESSAGE:[1, 2, 3]", "DISCONNECTED");
    assertThat(closed).isTrue();
    assertThat(connector.state()).isEqualTo(ConnectorState.DISCONNECTED);
  }

  @Test
  void closesTheSessionBeforePublishingTransportFailure() throws Exception {
    var closed = new AtomicBoolean();
    var closedBeforeFailure = new AtomicBoolean();
    var failed = new CountDownLatch(1);
    var connector =
        new TcpConnector(
            UUID.randomUUID(),
            _ -> {
              throw new IOException("broken socket");
            },
            () -> session(new byte[0], closed));
    connector.addListener(
        new ConnectorListener() {
          @Override
          public void onStateChange(UUID sourceId, ConnectorState state) {
            if (state == ConnectorState.FAILED) {
              closedBeforeFailure.set(closed.get());
              failed.countDown();
            }
          }
        });

    connector.start();

    assertThat(failed.await(1, TimeUnit.SECONDS)).isTrue();
    assertThat(closedBeforeFailure).isTrue();
    assertThat(connector.state()).isEqualTo(ConnectorState.FAILED);
  }

  @Test
  void terminalizesRawErrorBeforeRethrowingToTheUncaughtHandler() throws Exception {
    var fatal = new AssertionError("fatal framing");
    var closed = new AtomicBoolean();
    var states = new CopyOnWriteArrayList<ConnectorState>();
    var observedFailure = new AtomicReference<Throwable>();
    var uncaughtFailure = new AtomicReference<Throwable>();
    var failed = new CountDownLatch(1);
    var uncaught = new CountDownLatch(1);
    var connector =
        new TcpConnector(
            UUID.randomUUID(),
            _ -> {
              throw fatal;
            },
            () -> session(new byte[0], closed));
    connector.addListener(
        new ConnectorListener() {
          @Override
          public void onStateChange(UUID sourceId, ConnectorState state) {
            states.add(state);
            if (state == ConnectorState.FAILED) {
              failed.countDown();
            }
          }

          @Override
          public void onError(UUID sourceId, Throwable failure) {
            observedFailure.set(failure);
          }
        });
    var threadGroup =
        new ThreadGroup("raw-error-terminalization") {
          @Override
          public void uncaughtException(Thread thread, Throwable failure) {
            uncaughtFailure.set(failure);
            uncaught.countDown();
          }
        };

    var starter = new Thread(threadGroup, connector::start, "raw-error-starter");
    starter.start();
    starter.join(1_000);

    assertThat(failed.await(1, TimeUnit.SECONDS)).isTrue();
    assertThat(uncaught.await(1, TimeUnit.SECONDS)).isTrue();
    assertThat(closed).isTrue();
    assertThat(connector.state()).isEqualTo(ConnectorState.FAILED);
    assertThat(observedFailure).hasValue(fatal);
    assertThat(uncaughtFailure).hasValue(fatal);
    assertThat(states)
        .containsExactly(
            ConnectorState.CONNECTING, ConnectorState.CONNECTED, ConnectorState.FAILED);
  }

  @Test
  void publishesDisconnectedWhenSessionCloseFailsAfterEof() throws Exception {
    var disconnected = new CountDownLatch(1);
    var connector =
        new TcpConnector(
            UUID.randomUUID(),
            _ -> null,
            () -> closeThrowingSession(new ByteArrayInputStream(new byte[0])));
    connector.addListener(
        new ConnectorListener() {
          @Override
          public void onStateChange(UUID sourceId, ConnectorState state) {
            if (state == ConnectorState.DISCONNECTED) {
              disconnected.countDown();
            }
          }
        });

    connector.start();

    assertThat(disconnected.await(1, TimeUnit.SECONDS)).isTrue();
    assertThat(connector.state()).isEqualTo(ConnectorState.DISCONNECTED);
  }

  @Test
  void interruptsAndPublishesStoppedWhenSessionCloseFailsDuringStop() throws Exception {
    var readStarted = new CountDownLatch(1);
    var readInterrupted = new CountDownLatch(1);
    var stopped = new CountDownLatch(1);
    var connector =
        new TcpConnector(
            UUID.randomUUID(),
            _ -> {
              readStarted.countDown();
              try {
                new CountDownLatch(1).await(1, TimeUnit.SECONDS);
                return null;
              } catch (InterruptedException exception) {
                readInterrupted.countDown();
                Thread.currentThread().interrupt();
                throw new IOException("read interrupted", exception);
              }
            },
            () -> closeThrowingSession(new ByteArrayInputStream(new byte[0])));
    connector.addListener(
        new ConnectorListener() {
          @Override
          public void onStateChange(UUID sourceId, ConnectorState state) {
            if (state == ConnectorState.STOPPED) {
              stopped.countDown();
            }
          }
        });
    connector.start();
    assertThat(readStarted.await(1, TimeUnit.SECONDS)).isTrue();

    assertThatThrownBy(connector::stop)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("close failed");

    assertThat(readInterrupted.await(200, TimeUnit.MILLISECONDS)).isTrue();
    assertThat(stopped.await(200, TimeUnit.MILLISECONDS)).isTrue();
    assertThat(connector.state()).isEqualTo(ConnectorState.STOPPED);
  }

  @Test
  void treatsSocketReadTimeoutAsATransportWakeUpAndContinuesReading() throws Exception {
    var readAttempts = new AtomicInteger();
    var restartRequested = new AtomicBoolean();
    var disconnected = new CountDownLatch(1);
    var connector =
        new TcpConnector(
            UUID.randomUUID(),
            _ -> {
              if (readAttempts.incrementAndGet() == 1) {
                throw new SocketTimeoutException("transport wake-up");
              }
              return null;
            },
            () -> session(new byte[0], new AtomicBoolean()));
    connector.addListener(
        new ConnectorListener() {
          @Override
          public void onStateChange(UUID sourceId, ConnectorState state) {
            if (state == ConnectorState.DISCONNECTED) {
              disconnected.countDown();
            }
          }

          @Override
          public void onRestartRequested(UUID sourceId, Throwable reason) {
            restartRequested.set(true);
          }
        });

    connector.start();

    assertThat(disconnected.await(1, TimeUnit.SECONDS)).isTrue();
    assertThat(readAttempts).hasValue(2);
    assertThat(restartRequested).isFalse();
  }

  @Test
  void serializesConcurrentWritesAndStopClosesTheRawSession() throws Exception {
    var readStarted = new CountDownLatch(1);
    var closed = new AtomicBoolean();
    var output = new BlockingFirstOutputStream();
    var connector =
        new TcpConnector(
            UUID.randomUUID(),
            _ -> {
              readStarted.countDown();
              try {
                new CountDownLatch(1).await();
                return null;
              } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("read interrupted", exception);
              }
            },
            () -> session(new ByteArrayInputStream(new byte[0]), output, closed));
    connector.start();
    assertThat(readStarted.await(1, TimeUnit.SECONDS)).isTrue();

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var first =
          executor.submit(
              () -> {
                connector.send(new byte[] {1, 1, 1});
                return null;
              });
      assertThat(output.firstWriteStarted.await(1, TimeUnit.SECONDS)).isTrue();
      var second =
          executor.submit(
              () -> {
                connector.send(new byte[] {2, 2, 2});
                return null;
              });

      assertThat(second.isDone()).isFalse();
      assertThat(output.maximumConcurrentWrites).hasValue(1);

      output.releaseFirstWrite.countDown();
      first.get(1, TimeUnit.SECONDS);
      second.get(1, TimeUnit.SECONDS);
    } finally {
      output.releaseFirstWrite.countDown();
    }

    assertThat(output.bytes()).containsExactly(1, 1, 1, 2, 2, 2);
    assertThat(output.maximumConcurrentWrites).hasValue(1);
    assertThat(connector.stop()).isTrue();
    assertThat(closed).isTrue();
    assertThat(connector.state()).isEqualTo(ConnectorState.STOPPED);
  }

  private static Session session(byte[] inbound, AtomicBoolean closed) {
    return session(new ByteArrayInputStream(inbound), new ByteArrayOutputStream(), closed);
  }

  private static Session session(InputStream input, OutputStream output, AtomicBoolean closed) {
    return new Session() {
      @Override
      public InputStream input() {
        return input;
      }

      @Override
      public OutputStream output() {
        return output;
      }

      @Override
      public void close() {
        closed.set(true);
      }
    };
  }

  private static Session closeThrowingSession(InputStream input) {
    return new Session() {
      @Override
      public InputStream input() {
        return input;
      }

      @Override
      public OutputStream output() {
        return new ByteArrayOutputStream();
      }

      @Override
      public void close() {
        throw new IllegalStateException("close failed");
      }
    };
  }

  private static final class BlockingFirstOutputStream extends OutputStream {

    private final ByteArrayOutputStream delegate = new ByteArrayOutputStream();
    private final CountDownLatch firstWriteStarted = new CountDownLatch(1);
    private final CountDownLatch releaseFirstWrite = new CountDownLatch(1);
    private final AtomicInteger activeWrites = new AtomicInteger();
    private final AtomicInteger maximumConcurrentWrites = new AtomicInteger();

    @Override
    public void write(byte[] bytes, int offset, int length) throws IOException {
      int active = activeWrites.incrementAndGet();
      maximumConcurrentWrites.accumulateAndGet(active, Math::max);
      try {
        if (bytes[offset] == 1) {
          firstWriteStarted.countDown();
          awaitRelease();
        }
        delegate.write(bytes, offset, length);
      } finally {
        activeWrites.decrementAndGet();
      }
    }

    @Override
    public void write(int value) {
      delegate.write(value);
    }

    private byte[] bytes() {
      return delegate.toByteArray();
    }

    private void awaitRelease() throws IOException {
      try {
        releaseFirstWrite.await();
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new IOException("write interrupted", exception);
      }
    }
  }
}
