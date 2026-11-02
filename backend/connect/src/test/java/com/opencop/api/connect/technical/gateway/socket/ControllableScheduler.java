// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.gateway.socket;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Deterministic timing adapter for connection behavior tests. */
final class ControllableScheduler extends ScheduledThreadPoolExecutor {

  private final ArrayDeque<ControlledFuture> pending = new ArrayDeque<>();
  private final List<Duration> delays = new ArrayList<>();

  ControllableScheduler() {
    super(1);
  }

  @Override
  public synchronized ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
    var delayNanos = unit.toNanos(delay);
    var future = new ControlledFuture(command, delayNanos);
    pending.addLast(future);
    delays.add(Duration.ofNanos(delayNanos));
    notifyAll();
    return future;
  }

  synchronized List<Duration> delays() {
    return List.copyOf(delays);
  }

  synchronized List<Duration> pendingDelays() {
    return pending.stream()
        .filter(task -> !task.isCancelled())
        .map(ControlledFuture::delay)
        .toList();
  }

  synchronized boolean awaitScheduleCount(int expected, Duration timeout)
      throws InterruptedException {
    long deadline = System.nanoTime() + timeout.toNanos();
    while (delays.size() < expected) {
      long remaining = deadline - System.nanoTime();
      if (remaining <= 0) {
        return false;
      }
      TimeUnit.NANOSECONDS.timedWait(this, remaining);
    }
    return true;
  }

  void runNext() {
    ControlledFuture next;
    synchronized (this) {
      do {
        next = pending.removeFirst();
      } while (next.isCancelled());
    }
    next.runTask();
  }

  void runNext(Duration delay) {
    runNext(delay, null);
  }

  void runNext(Duration delay, CountDownLatch dispatchEntered) {
    ControlledFuture next;
    synchronized (this) {
      next =
          pending.stream()
              .filter(task -> !task.isCancelled())
              .filter(task -> task.delay().equals(delay))
              .findFirst()
              .orElseThrow(() -> new IllegalStateException("No pending task with delay " + delay));
      pending.remove(next);
    }
    next.runTask(dispatchEntered);
  }

  private static final class ControlledFuture implements ScheduledFuture<Object> {

    private final Runnable command;
    private final long delayNanos;
    private volatile boolean cancelled;
    private volatile boolean done;

    private ControlledFuture(Runnable command, long delayNanos) {
      this.command = command;
      this.delayNanos = delayNanos;
    }

    private Duration delay() {
      return Duration.ofNanos(delayNanos);
    }

    private void runTask() {
      runTask(null);
    }

    private void runTask(CountDownLatch dispatchEntered) {
      if (!cancelled) {
        done = true;
        if (dispatchEntered != null) {
          dispatchEntered.countDown();
        }
        command.run();
      }
    }

    @Override
    public long getDelay(TimeUnit unit) {
      return unit.convert(delayNanos, TimeUnit.NANOSECONDS);
    }

    @Override
    public int compareTo(Delayed other) {
      return Long.compare(getDelay(TimeUnit.NANOSECONDS), other.getDelay(TimeUnit.NANOSECONDS));
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      if (done) {
        return false;
      }
      cancelled = true;
      return true;
    }

    @Override
    public boolean isCancelled() {
      return cancelled;
    }

    @Override
    public boolean isDone() {
      return done || cancelled;
    }

    @Override
    public Object get() throws InterruptedException, ExecutionException {
      throw new UnsupportedOperationException();
    }

    @Override
    public Object get(long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
      throw new UnsupportedOperationException();
    }
  }
}
