// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.gateway.connection.extension;

import java.time.Duration;

/**
 * Runs a delete hook apart from the deletion, which waits for it at most a time limit. A hook still
 * running then is asked to stop, but nothing can force it to.
 */
public interface DeleteHookRunner {

  /**
   * Runs the hook and waits for it.
   *
   * @throws RuntimeException when the hook cannot be started, as during a shutdown
   */
  Outcome run(Hook hook, Duration limit);

  /** A delete hook's work. */
  @FunctionalInterface
  interface Hook {
    void run() throws Exception;
  }

  /** How a hook's run ended, as far as the deletion waited for it. */
  sealed interface Outcome {

    Outcome COMPLETED = new Completed();
    Outcome TIMED_OUT = new TimedOut();
    Outcome INTERRUPTED = new Interrupted();

    /** The hook returned. */
    record Completed() implements Outcome {}

    /** The hook threw. */
    record Failed(Throwable cause) implements Outcome {}

    /** The hook ran past the limit, and was asked to stop. */
    record TimedOut() implements Outcome {}

    /**
     * The waiting thread was interrupted, as by a shutdown: the hook was asked to stop, and the
     * thread keeps its interrupt.
     */
    record Interrupted() implements Outcome {}
  }
}
