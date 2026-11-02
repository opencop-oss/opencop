// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.gateway.socket;

import com.opencop.core.sdk.business.model.integration.connection.behavior.ConnectionBehaviorPlan.RecurringAction;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

final class RecurringBehaviorRunner {

  private final RecurringAction action;
  private final ScheduledThreadPoolExecutor scheduler;
  private final BehaviorTaskExecutor taskExecutor;
  private final MessageActionExecutor messageExecutor;

  private ScheduleSlot pending;
  private ExecutionSlot running;
  private boolean started;
  private boolean canceled;

  RecurringBehaviorRunner(
      RecurringAction action,
      ScheduledThreadPoolExecutor scheduler,
      BehaviorTaskExecutor taskExecutor,
      MessageActionExecutor messageExecutor) {
    this.action = Objects.requireNonNull(action, "action");
    this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    this.taskExecutor = Objects.requireNonNull(taskExecutor, "taskExecutor");
    this.messageExecutor = Objects.requireNonNull(messageExecutor, "messageExecutor");
  }

  void start() {
    ScheduleSlot schedule;
    synchronized (this) {
      if (started || canceled) {
        return;
      }
      started = true;
      schedule = reserveSchedule();
    }
    arm(schedule);
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
    ScheduledFuture<?> timer;
    Future<?> task;
    synchronized (this) {
      if (canceled) {
        return;
      }
      canceled = true;
      timer = pending == null ? null : pending.timer;
      pending = null;
      task = running == null ? null : running.task;
      running = null;
    }
    if (timer != null) {
      timer.cancel(false);
    }
    if (task != null) {
      task.cancel(interruptRunning);
    }
  }

  private void arm(ScheduleSlot schedule) {
    var timer =
        scheduler.schedule(
            () -> dispatch(schedule), action.delay().toNanos(), TimeUnit.NANOSECONDS);
    boolean cancelTimer;
    synchronized (this) {
      schedule.timer = timer;
      cancelTimer = canceled || pending != schedule;
    }
    if (cancelTimer) {
      timer.cancel(false);
    }
  }

  private void dispatch(ScheduleSlot schedule) {
    ExecutionSlot execution;
    synchronized (this) {
      if (canceled || pending != schedule) {
        return;
      }
      pending = null;
      execution = new ExecutionSlot();
      running = execution;
    }

    Future<?> task;
    try {
      task =
          taskExecutor.submit(
              action.message().name(), () -> awaitRegistrationAndAttempt(execution));
    } catch (RuntimeException | Error failure) {
      synchronized (this) {
        if (running == execution) {
          running = null;
        }
      }
      throw failure;
    }

    boolean cancelTask;
    synchronized (this) {
      execution.task = task;
      cancelTask = !execution.completed && (canceled || running != execution);
    }
    execution.registered.countDown();
    if (cancelTask) {
      task.cancel(true);
    }
  }

  private void awaitRegistrationAndAttempt(ExecutionSlot execution) {
    try {
      execution.registered.await();
    } catch (InterruptedException _) {
      Thread.currentThread().interrupt();
      return;
    }
    synchronized (this) {
      if (canceled || running != execution) {
        execution.completed = true;
        return;
      }
    }
    attemptAndRearm(execution);
  }

  private void attemptAndRearm(ExecutionSlot execution) {
    MessageAttemptOutcome outcome = null;
    try {
      outcome = messageExecutor.attempt(action.message());
    } finally {
      complete(execution, outcome);
    }
  }

  private void complete(ExecutionSlot execution, MessageAttemptOutcome outcome) {
    ScheduleSlot schedule = null;
    synchronized (this) {
      execution.completed = true;
      if (running != execution) {
        return;
      }
      running = null;
      if (outcome == MessageAttemptOutcome.COMPLETED && !canceled) {
        schedule = reserveSchedule();
      }
    }
    if (schedule != null) {
      arm(schedule);
    }
  }

  private ScheduleSlot reserveSchedule() {
    var schedule = new ScheduleSlot();
    pending = schedule;
    return schedule;
  }

  private static final class ScheduleSlot {

    private ScheduledFuture<?> timer;
  }

  private static final class ExecutionSlot {

    private final CountDownLatch registered = new CountDownLatch(1);
    private Future<?> task;
    private boolean completed;
  }
}
