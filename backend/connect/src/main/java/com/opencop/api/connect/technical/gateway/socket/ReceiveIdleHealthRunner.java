// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.gateway.socket;

import com.opencop.core.sdk.business.model.integration.connection.behavior.ConnectionBehaviorPlan.IdleMessageAction;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

final class ReceiveIdleHealthRunner {

  private static final String RECONNECT_BEHAVIOR_NAME = "receive-idle-reconnect";

  private final Optional<IdleMessageAction> idleMessage;
  private final Optional<Duration> reconnectThreshold;
  private final ScheduledThreadPoolExecutor scheduler;
  private final BehaviorTaskExecutor taskExecutor;
  private final MessageActionExecutor messageExecutor;
  private final Clock clock;
  private final Consumer<SocketTimeoutException> reconnectCallback;

  private TimerSlot pingTimer;
  private TimerSlot reconnectTimer;
  private ExecutionSlot pingRunning;
  private ExecutionSlot reconnectRunning;
  private Instant lastReceiveAt;
  private Instant lastPingCompletedAt;
  private long generation;
  private boolean started;
  private boolean canceled;

  ReceiveIdleHealthRunner(
      Optional<IdleMessageAction> idleMessage,
      Optional<Duration> reconnectThreshold,
      ScheduledThreadPoolExecutor scheduler,
      BehaviorTaskExecutor taskExecutor,
      MessageActionExecutor messageExecutor,
      Clock clock,
      Consumer<SocketTimeoutException> reconnectCallback) {
    this.idleMessage = Objects.requireNonNull(idleMessage, "idleMessage");
    this.reconnectThreshold = Objects.requireNonNull(reconnectThreshold, "reconnectThreshold");
    this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    this.taskExecutor = Objects.requireNonNull(taskExecutor, "taskExecutor");
    this.messageExecutor = Objects.requireNonNull(messageExecutor, "messageExecutor");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.reconnectCallback = Objects.requireNonNull(reconnectCallback, "reconnectCallback");
  }

  void start() {
    List<TimerRequest> requests;
    synchronized (this) {
      if (started || canceled) {
        return;
      }
      started = true;
      if (idleMessage.isEmpty() && reconnectThreshold.isEmpty()) {
        return;
      }
      lastReceiveAt = clock.instant();
      generation++;
      requests = reserveMissingTimers();
    }
    armAll(requests);
  }

  void recordReceiveActivity() {
    List<ScheduledFuture<?>> timers;
    List<TimerRequest> requests;
    synchronized (this) {
      if (!started || canceled) {
        return;
      }
      lastReceiveAt = clock.instant();
      generation++;
      timers = detachTimers();
      requests = reserveMissingTimers();
    }
    cancelTimers(timers);
    armAll(requests);
  }

  void cancel() {
    cancel(true);
  }

  /**
   * Stops this runner. With {@code interruptRunning} false, work already sending finishes instead
   * of being interrupted: interrupting a virtual thread blocked in a socket write closes the
   * socket.
   */
  void cancel(boolean interruptRunning) {
    List<ScheduledFuture<?>> timers;
    List<Future<?>> tasks;
    synchronized (this) {
      if (canceled) {
        return;
      }
      canceled = true;
      generation++;
      timers = detachTimers();
      tasks = detachExecutions();
    }
    cancelTimers(timers);
    tasks.forEach(task -> task.cancel(interruptRunning));
  }

  private List<TimerRequest> reserveMissingTimers() {
    var requests = new ArrayList<TimerRequest>(2);
    if (idleMessage.isPresent() && pingTimer == null && pingRunning == null) {
      requests.add(reserveTimer(WorkKind.PING, remainingPing(clock.instant())));
    }
    if (reconnectThreshold.isPresent() && reconnectTimer == null && reconnectRunning == null) {
      requests.add(reserveTimer(WorkKind.RECONNECT, remainingReconnect(clock.instant())));
    }
    return List.copyOf(requests);
  }

  private TimerRequest reserveTimer(WorkKind kind, Duration delay) {
    var slot = new TimerSlot(kind, generation);
    setTimer(kind, slot);
    return new TimerRequest(slot, delay);
  }

  private void armAll(List<TimerRequest> requests) {
    requests.forEach(this::arm);
  }

  private void arm(TimerRequest request) {
    ScheduledFuture<?> timer;
    try {
      timer =
          scheduler.schedule(
              () -> dispatch(request.slot), request.delay.toNanos(), TimeUnit.NANOSECONDS);
    } catch (RuntimeException | Error failure) {
      synchronized (this) {
        if (timer(request.slot.kind) == request.slot) {
          setTimer(request.slot.kind, null);
        }
      }
      throw failure;
    }

    boolean cancelTimer;
    synchronized (this) {
      request.slot.timer = timer;
      cancelTimer =
          canceled
              || request.slot.generation != generation
              || timer(request.slot.kind) != request.slot;
    }
    if (cancelTimer) {
      timer.cancel(false);
    }
  }

  private void dispatch(TimerSlot timer) {
    ExecutionSlot execution;
    synchronized (this) {
      if (canceled || timer.generation != generation || timer(timer.kind) != timer) {
        return;
      }
      setTimer(timer.kind, null);
      execution = new ExecutionSlot(timer.kind);
      setExecution(timer.kind, execution);
    }

    Future<?> task;
    try {
      task =
          taskExecutor.submit(
              behaviorName(timer.kind), () -> awaitRegistrationAndExecute(execution));
    } catch (RuntimeException | Error failure) {
      synchronized (this) {
        if (execution(timer.kind) == execution) {
          setExecution(timer.kind, null);
        }
      }
      throw failure;
    }

    boolean cancelTask;
    synchronized (this) {
      execution.task = task;
      cancelTask = !execution.completed && (canceled || execution(timer.kind) != execution);
    }
    execution.registered.countDown();
    if (cancelTask) {
      task.cancel(true);
    }
  }

  private void awaitRegistrationAndExecute(ExecutionSlot execution) {
    try {
      execution.registered.await();
    } catch (InterruptedException _) {
      Thread.currentThread().interrupt();
      return;
    }
    synchronized (this) {
      if (canceled || execution(execution.kind) != execution) {
        execution.completed = true;
        return;
      }
    }
    if (execution.kind == WorkKind.PING) {
      executePing(execution);
    } else {
      executeReconnect(execution);
    }
  }

  private void executePing(ExecutionSlot execution) {
    TimerRequest earlyRearm = null;
    boolean attemptMessage = false;
    synchronized (this) {
      if (canceled || pingRunning != execution) {
        execution.completed = true;
        return;
      }
      var now = clock.instant();
      var remaining = remainingPing(now);
      if (!remaining.isZero()) {
        pingRunning = null;
        execution.completed = true;
        earlyRearm = reserveTimer(WorkKind.PING, remaining);
      } else if (reconnectThreshold.isPresent() && remainingReconnect(now).isZero()) {
        pingRunning = null;
        execution.completed = true;
      } else {
        attemptMessage = true;
      }
    }
    if (earlyRearm != null) {
      arm(earlyRearm);
      return;
    }
    if (!attemptMessage) {
      return;
    }

    MessageAttemptOutcome outcome = null;
    try {
      outcome = messageExecutor.attempt(idleMessage.orElseThrow().message());
    } finally {
      completePing(execution, outcome);
    }
  }

  private void completePing(ExecutionSlot execution, MessageAttemptOutcome outcome) {
    TimerRequest rearm = null;
    synchronized (this) {
      execution.completed = true;
      if (pingRunning != execution) {
        return;
      }
      pingRunning = null;
      if (outcome == MessageAttemptOutcome.COMPLETED && !canceled) {
        lastPingCompletedAt = clock.instant();
        rearm = reserveTimer(WorkKind.PING, remainingPing(clock.instant()));
      }
    }
    if (rearm != null) {
      arm(rearm);
    }
  }

  private void executeReconnect(ExecutionSlot execution) {
    TimerRequest earlyRearm = null;
    List<ScheduledFuture<?>> timers = List.of();
    Future<?> pingTask = null;
    SocketTimeoutException reason = null;
    synchronized (this) {
      if (canceled || reconnectRunning != execution) {
        execution.completed = true;
        return;
      }
      var now = clock.instant();
      var remaining = remainingReconnect(now);
      if (!remaining.isZero()) {
        reconnectRunning = null;
        execution.completed = true;
        earlyRearm = reserveTimer(WorkKind.RECONNECT, remaining);
      } else {
        canceled = true;
        generation++;
        execution.completed = true;
        reconnectRunning = null;
        timers = detachTimers();
        pingTask = pingRunning == null ? null : pingRunning.task;
        pingRunning = null;
        var idleMillis = Duration.between(lastReceiveAt, now).toMillis();
        var thresholdMillis = reconnectThreshold.orElseThrow().toMillis();
        reason =
            new SocketTimeoutException(
                "No data received for " + idleMillis + "ms (threshold: " + thresholdMillis + "ms)");
      }
    }

    if (earlyRearm != null) {
      arm(earlyRearm);
      return;
    }
    cancelTimers(timers);
    if (pingTask != null) {
      pingTask.cancel(true);
    }
    reconnectCallback.accept(reason);
  }

  private Duration remainingPing(Instant now) {
    var baseline =
        lastPingCompletedAt != null && lastPingCompletedAt.isAfter(lastReceiveAt)
            ? lastPingCompletedAt
            : lastReceiveAt;
    return remaining(now, baseline, idleMessage.orElseThrow().threshold());
  }

  private Duration remainingReconnect(Instant now) {
    return remaining(now, lastReceiveAt, reconnectThreshold.orElseThrow());
  }

  private static Duration remaining(Instant now, Instant baseline, Duration threshold) {
    var value = Duration.between(now, baseline.plus(threshold));
    return value.isNegative() ? Duration.ZERO : value;
  }

  private List<ScheduledFuture<?>> detachTimers() {
    var timers = new ArrayList<ScheduledFuture<?>>(2);
    if (pingTimer != null && pingTimer.timer != null) {
      timers.add(pingTimer.timer);
    }
    if (reconnectTimer != null && reconnectTimer.timer != null) {
      timers.add(reconnectTimer.timer);
    }
    pingTimer = null;
    reconnectTimer = null;
    return List.copyOf(timers);
  }

  private List<Future<?>> detachExecutions() {
    var tasks = new ArrayList<Future<?>>(2);
    if (pingRunning != null && pingRunning.task != null) {
      tasks.add(pingRunning.task);
    }
    if (reconnectRunning != null && reconnectRunning.task != null) {
      tasks.add(reconnectRunning.task);
    }
    pingRunning = null;
    reconnectRunning = null;
    return List.copyOf(tasks);
  }

  private static void cancelTimers(List<ScheduledFuture<?>> timers) {
    timers.forEach(timer -> timer.cancel(false));
  }

  private String behaviorName(WorkKind kind) {
    return kind == WorkKind.PING
        ? idleMessage.orElseThrow().message().name()
        : RECONNECT_BEHAVIOR_NAME;
  }

  private TimerSlot timer(WorkKind kind) {
    return kind == WorkKind.PING ? pingTimer : reconnectTimer;
  }

  private void setTimer(WorkKind kind, TimerSlot timer) {
    if (kind == WorkKind.PING) {
      pingTimer = timer;
    } else {
      reconnectTimer = timer;
    }
  }

  private ExecutionSlot execution(WorkKind kind) {
    return kind == WorkKind.PING ? pingRunning : reconnectRunning;
  }

  private void setExecution(WorkKind kind, ExecutionSlot execution) {
    if (kind == WorkKind.PING) {
      pingRunning = execution;
    } else {
      reconnectRunning = execution;
    }
  }

  private enum WorkKind {
    PING,
    RECONNECT
  }

  private static final class TimerSlot {

    private final WorkKind kind;
    private final long generation;
    private ScheduledFuture<?> timer;

    private TimerSlot(WorkKind kind, long generation) {
      this.kind = kind;
      this.generation = generation;
    }
  }

  private static final class ExecutionSlot {

    private final WorkKind kind;
    private final CountDownLatch registered = new CountDownLatch(1);
    private Future<?> task;
    private boolean completed;

    private ExecutionSlot(WorkKind kind) {
      this.kind = kind;
    }
  }

  private record TimerRequest(TimerSlot slot, Duration delay) {}
}
