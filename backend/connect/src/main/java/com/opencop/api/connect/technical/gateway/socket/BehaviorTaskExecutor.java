// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.gateway.socket;

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.function.BiConsumer;

final class BehaviorTaskExecutor {

  private final ExecutorService executor;
  private final BiConsumer<String, Error> fatalCallback;

  BehaviorTaskExecutor(ExecutorService executor, BiConsumer<String, Error> fatalCallback) {
    this.executor = Objects.requireNonNull(executor, "executor");
    this.fatalCallback = Objects.requireNonNull(fatalCallback, "fatalCallback");
  }

  Future<?> submit(String behaviorName, Runnable action) {
    var task = new ObservedBehaviorTask(behaviorName, action);
    executor.execute(task);
    return task;
  }

  private final class ObservedBehaviorTask extends FutureTask<Void> {

    private final String behaviorName;

    private ObservedBehaviorTask(String behaviorName, Runnable action) {
      super(Objects.requireNonNull(action, "action"), null);
      this.behaviorName = Objects.requireNonNull(behaviorName, "behaviorName");
    }

    @Override
    protected void done() {
      if (isCancelled()) {
        return;
      }
      try {
        get();
      } catch (CancellationException _) {
        // Intentional stop or another terminal transition owns cancellation.
      } catch (InterruptedException _) {
        Thread.currentThread().interrupt();
      } catch (ExecutionException exception) {
        var cause = exception.getCause();
        if (cause instanceof Error error) {
          fatalCallback.accept(behaviorName, error);
        }
        rethrowFailure(cause);
      }
    }
  }

  static void rethrowFailure(Throwable cause) {
    if (cause instanceof Error error) {
      throw error;
    }
    if (cause instanceof RuntimeException runtimeException) {
      throw runtimeException;
    }
    throw new IllegalStateException("Connection behavior execution failed", cause);
  }
}
