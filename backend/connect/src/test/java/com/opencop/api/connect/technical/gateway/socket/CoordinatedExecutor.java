// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.gateway.socket;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

final class CoordinatedExecutor extends AbstractExecutorService {

  private final ExecutorService delegate = Executors.newVirtualThreadPerTaskExecutor();
  private final AtomicReference<ExecutionGate> nextGate = new AtomicReference<>();
  private final AtomicReference<SubmissionGate> nextSubmission = new AtomicReference<>();
  private final AtomicReference<CountDownLatch> nextCompletion = new AtomicReference<>();
  private final AtomicBoolean observedCompletionInterrupted = new AtomicBoolean();

  void coordinateNext(int taskCount) {
    nextGate.set(new ExecutionGate(taskCount));
  }

  void blockNextSubmission(CountDownLatch entered, CountDownLatch release) {
    nextSubmission.set(new SubmissionGate(entered, release));
  }

  boolean awaitCoordinated(Duration timeout) throws InterruptedException {
    return nextGate.get().arrived().await(timeout.toNanos(), TimeUnit.NANOSECONDS);
  }

  void releaseCoordinated() {
    var gate = nextGate.getAndSet(null);
    if (gate != null) {
      gate.release().countDown();
    }
  }

  void observeNextCompletion(CountDownLatch completion) {
    if (!nextCompletion.compareAndSet(null, completion)) {
      throw new IllegalStateException("A task completion is already being observed");
    }
  }

  boolean observedCompletionWasInterrupted() {
    return observedCompletionInterrupted.get();
  }

  @Override
  public void execute(Runnable command) {
    var submission = nextSubmission.getAndSet(null);
    if (submission != null) {
      submission.awaitRelease();
    }
    var gate = nextGate.get();
    var completion = nextCompletion.getAndSet(null);
    delegate.execute(
        () -> {
          try {
            if (gate != null && gate.claim()) {
              gate.arrived().countDown();
              try {
                gate.release().await();
              } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
                return;
              }
            }
            command.run();
          } finally {
            if (completion != null) {
              observedCompletionInterrupted.set(Thread.currentThread().isInterrupted());
              completion.countDown();
            }
          }
        });
  }

  @Override
  public void shutdown() {
    releaseCoordinated();
    delegate.shutdown();
  }

  @Override
  public List<Runnable> shutdownNow() {
    releaseCoordinated();
    return delegate.shutdownNow();
  }

  @Override
  public boolean isShutdown() {
    return delegate.isShutdown();
  }

  @Override
  public boolean isTerminated() {
    return delegate.isTerminated();
  }

  @Override
  public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
    return delegate.awaitTermination(timeout, unit);
  }

  private static final class ExecutionGate {

    private final AtomicInteger remainingClaims;
    private final CountDownLatch arrived;
    private final CountDownLatch release = new CountDownLatch(1);

    private ExecutionGate(int taskCount) {
      remainingClaims = new AtomicInteger(taskCount);
      arrived = new CountDownLatch(taskCount);
    }

    boolean claim() {
      return remainingClaims.getAndUpdate(remaining -> Math.max(0, remaining - 1)) > 0;
    }

    CountDownLatch arrived() {
      return arrived;
    }

    CountDownLatch release() {
      return release;
    }
  }

  private record SubmissionGate(CountDownLatch entered, CountDownLatch release) {

    private void awaitRelease() {
      entered.countDown();
      try {
        release.await();
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new RejectedExecutionException(
            "Interrupted while blocking task submission", exception);
      }
    }
  }
}
