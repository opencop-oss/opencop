// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.spring.websocket;

import jakarta.websocket.SendHandler;
import jakarta.websocket.SendResult;
import jakarta.websocket.Session;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.jetty.websocket.api.Callback;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.adapter.NativeWebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

public class AckingWebSocketSessionDecorator extends ConcurrentWebSocketSessionDecorator {
  private static final ScheduledThreadPoolExecutor TIMEOUT_SCHEDULER = createTimeoutScheduler();
  private final int sendTimeLimitMs;
  private final Object awaitLock = new Object();
  private final AtomicBoolean transportCloseInitiated = new AtomicBoolean();
  private CompletableFuture<Void> lastAwaitedSend = CompletableFuture.completedFuture(null);

  public AckingWebSocketSessionDecorator(
      WebSocketSession delegate, int sendTimeLimit, int bufferSizeLimit) {
    super(delegate, sendTimeLimit, bufferSizeLimit);
    this.sendTimeLimitMs = sendTimeLimit;
  }

  public CompletableFuture<Void> sendBinaryAwait(byte[] payload) {
    if (transportCloseInitiated.get() || !isOpen()) {
      return failedFuture(new IOException("session closed before send"));
    }

    CompletableFuture<Void> queued = new CompletableFuture<>();
    CompletableFuture<Void> previous;
    synchronized (awaitLock) {
      previous = lastAwaitedSend;
      lastAwaitedSend = queued;
    }

    previous.whenComplete((ignore, priorError) -> startAwaitedSend(payload, queued));
    return queued;
  }

  private void startAwaitedSend(byte[] payload, CompletableFuture<Void> result) {
    if (transportCloseInitiated.get() || !isOpen()) {
      result.completeExceptionally(
          new CompletionException(new IOException("session closed before send")));
      return;
    }

    WebSocketSession inner = getDelegate();
    if (!(inner instanceof NativeWebSocketSession nws)) {
      result.completeExceptionally(
          new CompletionException(
              new IOException(
                  "native websocket session does not expose an async binary send API")));
      return;
    }

    Session jakartaSession = nws.getNativeSession(Session.class);
    if (jakartaSession != null) {
      sendViaJakartaAsync(jakartaSession, payload, result);
      return;
    }

    org.eclipse.jetty.websocket.api.Session jettySession =
        nws.getNativeSession(org.eclipse.jetty.websocket.api.Session.class);
    if (jettySession != null) {
      sendViaJettyAsync(jettySession, payload, result);
      return;
    }

    result.completeExceptionally(
        new CompletionException(
            new IOException("native websocket session does not expose an async binary send API")));
  }

  private void sendViaJakartaAsync(
      Session jakartaSession, byte[] payload, CompletableFuture<Void> result) {
    AtomicBoolean done = new AtomicBoolean(false);
    ScheduledFuture<?> timeoutTask = scheduleTimeout(result, done);
    ByteBuffer buffer = ByteBuffer.wrap(payload);
    SendHandler handler =
        (SendResult sendResult) -> {
          if (!done.compareAndSet(false, true)) {
            return;
          }
          timeoutTask.cancel(false);
          if (sendResult.isOK()) {
            if (!isOpen()) {
              result.completeExceptionally(
                  new CompletionException(new IOException("session closed during send")));
            } else {
              result.complete(null);
            }
          } else {
            result.completeExceptionally(new CompletionException(sendResult.getException()));
          }
        };
    jakartaSession.getAsyncRemote().sendBinary(buffer, handler);
  }

  private void sendViaJettyAsync(
      org.eclipse.jetty.websocket.api.Session jettySession,
      byte[] payload,
      CompletableFuture<Void> result) {
    AtomicBoolean done = new AtomicBoolean(false);
    ScheduledFuture<?> timeoutTask = scheduleTimeout(result, done);
    jettySession.sendBinary(
        ByteBuffer.wrap(payload),
        Callback.from(
            () -> {
              if (!done.compareAndSet(false, true)) {
                return;
              }
              timeoutTask.cancel(false);
              if (!isOpen()) {
                result.completeExceptionally(
                    new CompletionException(new IOException("session closed during send")));
              } else {
                result.complete(null);
              }
            },
            t -> {
              if (!done.compareAndSet(false, true)) {
                return;
              }
              timeoutTask.cancel(false);
              result.completeExceptionally(new CompletionException(t));
            }));
  }

  private ScheduledFuture<?> scheduleTimeout(CompletableFuture<Void> result, AtomicBoolean done) {
    return TIMEOUT_SCHEDULER.schedule(
        () -> {
          if (done.compareAndSet(false, true)) {
            result.completeExceptionally(
                new CompletionException(
                    new TimeoutException(
                        "websocket binary send timed out after " + sendTimeLimitMs + " ms")));
          }
        },
        sendTimeLimitMs,
        TimeUnit.MILLISECONDS);
  }

  static ScheduledThreadPoolExecutor timeoutSchedulerForTests() {
    return TIMEOUT_SCHEDULER;
  }

  @Override
  public void close() throws IOException {
    close(CloseStatus.NORMAL);
  }

  @Override
  public void close(CloseStatus status) throws IOException {
    if (transportCloseInitiated.compareAndSet(false, true)) {
      getDelegate().close(status);
    }
  }

  private static ScheduledThreadPoolExecutor createTimeoutScheduler() {
    AtomicInteger threadNumber = new AtomicInteger();
    ScheduledThreadPoolExecutor scheduler =
        new ScheduledThreadPoolExecutor(
            1,
            runnable -> {
              Thread thread = new Thread(runnable);
              thread.setName(
                  "acking-websocket-timeout-scheduler-" + threadNumber.incrementAndGet());
              thread.setDaemon(true);
              return thread;
            });
    scheduler.setRemoveOnCancelPolicy(true);
    return scheduler;
  }

  private CompletableFuture<Void> failedFuture(IOException e) {
    CompletableFuture<Void> failed = new CompletableFuture<>();
    failed.completeExceptionally(new CompletionException(e));
    return failed;
  }
}
