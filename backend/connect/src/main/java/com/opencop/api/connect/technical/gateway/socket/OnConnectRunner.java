// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.gateway.socket;

import com.opencop.core.sdk.business.model.integration.connection.behavior.ConnectionBehaviorPlan.MessageAction;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.function.BiConsumer;

final class OnConnectRunner {

  private static final String BEHAVIOR_NAME = "on-connect";

  private final List<MessageAction> actions;
  private final ExecutorService executor;
  private final MessageActionExecutor messageExecutor;
  private final BiConsumer<String, Error> fatalCallback;
  private final BehaviorTaskExecutor backgroundTasks;

  private Future<?> activeTask;
  private boolean canceled;

  OnConnectRunner(
      List<MessageAction> actions,
      ExecutorService executor,
      MessageActionExecutor messageExecutor,
      BiConsumer<String, Error> fatalCallback) {
    this.actions = List.copyOf(Objects.requireNonNull(actions, "actions"));
    this.executor = Objects.requireNonNull(executor, "executor");
    this.messageExecutor = Objects.requireNonNull(messageExecutor, "messageExecutor");
    this.fatalCallback = Objects.requireNonNull(fatalCallback, "fatalCallback");
    this.backgroundTasks = new BehaviorTaskExecutor(executor, fatalCallback);
  }

  /**
   * Starts the actions without waiting for their sends, for callers that must not block on the peer
   * or absorb a behavior failure. A fatal failure reaches the fatal callback from the behavior
   * thread.
   */
  void runInBackground() {
    synchronized (this) {
      if (canceled) {
        return;
      }
      activeTask = backgroundTasks.submit(BEHAVIOR_NAME, this::attemptActions);
    }
  }

  void run() {
    var task = new FutureTask<Void>(this::attemptActions, null);
    synchronized (this) {
      if (canceled) {
        return;
      }
      activeTask = task;
    }
    try {
      executor.execute(task);
    } catch (RuntimeException | Error failure) {
      task.cancel(false);
      synchronized (this) {
        if (activeTask == task) {
          activeTask = null;
        }
      }
      throw failure;
    }
    try {
      task.get();
    } catch (InterruptedException _) {
      task.cancel(true);
      Thread.currentThread().interrupt();
    } catch (CancellationException _) {
      // Intentional stop or terminal state already owns cancellation and raw connector closure.
    } catch (ExecutionException exception) {
      var cause = exception.getCause();
      if (cause instanceof Error error) {
        fatalCallback.accept(BEHAVIOR_NAME, error);
      }
      BehaviorTaskExecutor.rethrowFailure(cause);
    } finally {
      synchronized (this) {
        if (activeTask == task) {
          activeTask = null;
        }
      }
    }
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
    Future<?> task;
    synchronized (this) {
      if (canceled) {
        return;
      }
      canceled = true;
      task = activeTask;
      activeTask = null;
    }
    if (task != null) {
      task.cancel(interruptRunning);
    }
  }

  private synchronized boolean isCanceled() {
    return canceled;
  }

  private void attemptActions() {
    for (var action : actions) {
      if (isCanceled() || messageExecutor.attempt(action) == MessageAttemptOutcome.INTERRUPTED) {
        return;
      }
    }
  }
}
