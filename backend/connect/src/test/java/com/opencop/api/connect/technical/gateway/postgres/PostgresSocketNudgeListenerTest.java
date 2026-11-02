// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.gateway.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.opencop.api.connect.business.reconciliation.DesiredOwnershipReconciler;
import com.opencop.core.sdk.model.nudge.SocketReconciliationNudge;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.PGConnection;
import tools.jackson.databind.ObjectMapper;

class PostgresSocketNudgeListenerTest {

  private ObjectMapper objectMapper;
  private DesiredOwnershipReconciler reconciler;
  private PostgresNudgeListenerConnectionSource connectionSource;
  private PostgresSocketNudgeListener listener;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    reconciler = mock(DesiredOwnershipReconciler.class);
    connectionSource = mock(PostgresNudgeListenerConnectionSource.class);
    listener =
        new PostgresSocketNudgeListener(
            connectionSource, objectMapper, reconciler, Duration.ofSeconds(5));
  }

  @Test
  void handlePayload_whenValidVersionOnlyNudgeInvokesReconcile() throws Exception {
    try (var running = startRunningListener()) {
      listener.handlePayload(payload());

      verify(reconciler).reconcileOnce();
    }
  }

  @Test
  void handlePayload_whenImmediateDuplicateCoalescesReconcile() throws Exception {
    try (var running = startRunningListener()) {
      listener.handlePayload(payload());
      listener.handlePayload(payload());

      verify(reconciler, times(1)).reconcileOnce();
    }
  }

  @Test
  void handlePayload_whenMalformedIgnoresPayload() {
    listener.handlePayload("{");

    verifyNoInteractions(reconciler);
  }

  @Test
  void handlePayload_whenUnsupportedVersionIgnoresPayload() throws Exception {
    try (var running = startRunningListener()) {
      var payload = objectMapper.writeValueAsString(new SocketReconciliationNudge(99));

      listener.handlePayload(payload);

      verifyNoInteractions(reconciler);
    }
  }

  @Test
  void registerListenAndReconcile_registersChannelAndRunsSweep() throws Exception {
    var connection = mock(Connection.class);
    var statement = mock(Statement.class);
    when(connection.createStatement()).thenReturn(statement);

    listener.registerListenAndReconcile(connection);

    verify(statement).execute("LISTEN " + SocketReconciliationNudge.CHANNEL);
    verify(statement).close();
    verify(reconciler).reconcileOnce();
  }

  @Test
  void stopWithCallback_waitsForListenerExitBeforeCallback() throws Exception {
    var connection = mock(Connection.class);
    var statement = mock(Statement.class);
    var pgConnection = mock(PGConnection.class);
    var polling = new CountDownLatch(1);
    var closeCalled = new AtomicBoolean(false);
    var notificationsReturned = new AtomicBoolean(false);
    when(connectionSource.getConnection()).thenReturn(connection);
    when(connection.createStatement()).thenReturn(statement);
    when(connection.unwrap(PGConnection.class)).thenReturn(pgConnection);
    when(pgConnection.getNotifications(1_000))
        .thenAnswer(
            _ -> {
              polling.countDown();
              var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
              while (!closeCalled.get() && System.nanoTime() < deadline) {
                try {
                  Thread.sleep(10);
                } catch (InterruptedException ignored) {
                  // stop() interrupts the listener; keep waiting for close.
                }
              }
              assertThat(closeCalled).isTrue();
              notificationsReturned.set(true);
              return null;
            });
    org.mockito.Mockito.doAnswer(
            _ -> {
              closeCalled.set(true);
              return null;
            })
        .when(connection)
        .close();

    listener.start();
    assertThat(polling.await(1, TimeUnit.SECONDS)).isTrue();

    var callbackRanAfterNotificationReturned = new AtomicBoolean(false);
    listener.stop(() -> callbackRanAfterNotificationReturned.set(notificationsReturned.get()));

    assertThat(callbackRanAfterNotificationReturned).isTrue();
  }

  @Test
  void stopWithCallback_whenJoinTimesOutStillRunsCallback() throws Exception {
    var release = new AtomicBoolean(false);
    var borrowStarted = new CountDownLatch(1);
    when(connectionSource.getConnection())
        .thenAnswer(
            _ -> {
              borrowStarted.countDown();
              while (!release.get()) {
                try {
                  Thread.sleep(10);
                } catch (InterruptedException ignored) {
                  // Keep the fake borrow alive until the test releases it.
                }
              }
              throw new SQLException("released");
            });
    var timeoutListener =
        new PostgresSocketNudgeListener(
            connectionSource, objectMapper, reconciler, Duration.ofMillis(10));

    timeoutListener.start();
    assertThat(borrowStarted.await(1, TimeUnit.SECONDS)).isTrue();

    var callbackRan = new AtomicBoolean(false);
    timeoutListener.stop(() -> callbackRan.set(true));
    release.set(true);
    timeoutListener.stop(() -> {});

    assertThat(callbackRan).isTrue();
  }

  private RunningListener startRunningListener() throws Exception {
    var release = new AtomicBoolean(false);
    var borrowStarted = new CountDownLatch(1);
    when(connectionSource.getConnection())
        .thenAnswer(
            _ -> {
              borrowStarted.countDown();
              while (!release.get()) {
                try {
                  Thread.sleep(10);
                } catch (InterruptedException ignored) {
                  // The test releases the fake borrow during cleanup.
                }
              }
              throw new SQLException("released");
            });

    // Keep the listener thread blocked before notification handling. These tests call
    // handlePayload synchronously only to exercise parsing/throttle with running=true.
    listener.start();
    assertThat(borrowStarted.await(1, TimeUnit.SECONDS)).isTrue();
    return new RunningListener(listener, release);
  }

  private String payload() {
    return objectMapper.writeValueAsString(SocketReconciliationNudge.current());
  }

  private record RunningListener(PostgresSocketNudgeListener listener, AtomicBoolean release)
      implements AutoCloseable {
    @Override
    public void close() {
      listener.stop();
      release.set(true);
      listener.stop(() -> {});
    }
  }
}
