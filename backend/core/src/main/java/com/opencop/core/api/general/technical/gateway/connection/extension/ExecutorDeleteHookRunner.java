// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.connection.extension;

import com.opencop.core.api.general.ports.gateway.connection.extension.DeleteHookRunner;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Runs each delete hook as its own task of the executor, cancelled once its limit passes. */
public class ExecutorDeleteHookRunner implements DeleteHookRunner {

  private final ExecutorService executor;

  public ExecutorDeleteHookRunner(ExecutorService executor) {
    this.executor = executor;
  }

  @Override
  public Outcome run(Hook hook, Duration limit) {
    var task =
        executor.submit(
            () -> {
              hook.run();
              return null;
            });
    try {
      task.get(limit.toMillis(), TimeUnit.MILLISECONDS);
      return Outcome.COMPLETED;
    } catch (TimeoutException expired) {
      task.cancel(true);
      return Outcome.TIMED_OUT;
    } catch (ExecutionException failed) {
      return new Outcome.Failed(failed.getCause());
    } catch (InterruptedException interrupted) {
      task.cancel(true);
      Thread.currentThread().interrupt();
      return Outcome.INTERRUPTED;
    }
  }
}
