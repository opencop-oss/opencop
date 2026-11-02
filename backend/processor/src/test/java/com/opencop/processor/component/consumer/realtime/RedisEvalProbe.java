// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.component.consumer.realtime;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class RedisEvalProbe {

  public record EvalInvocation(String scriptSha, int keyCount, byte[][] arguments) {}

  private final List<EvalInvocation> evalInvocations = new ArrayList<>();
  private int scriptLoadCalls;

  public synchronized void recordEval(String scriptSha, int keyCount, byte[][] arguments) {
    evalInvocations.add(new EvalInvocation(scriptSha, keyCount, arguments));
    notifyAll();
  }

  public synchronized void recordScriptLoad() {
    scriptLoadCalls++;
  }

  public synchronized List<EvalInvocation> awaitEvalCount(int expected, Duration timeout)
      throws InterruptedException {
    long deadline = System.nanoTime() + timeout.toNanos();
    while (evalInvocations.size() < expected && System.nanoTime() < deadline) {
      long remainingMs = Duration.ofNanos(deadline - System.nanoTime()).toMillis();
      wait(Math.max(1L, remainingMs));
    }
    if (evalInvocations.size() < expected) {
      throw new AssertionError(
          "Timed out waiting for %d Redis eval calls, observed %d"
              .formatted(expected, evalInvocations.size()));
    }
    return List.copyOf(evalInvocations);
  }

  public synchronized List<EvalInvocation> awaitExactEvalCount(
      int expected, Duration timeout, Duration quietPeriod) throws InterruptedException {
    long deadline = System.nanoTime() + timeout.toNanos();
    while (evalInvocations.size() < expected && System.nanoTime() < deadline) {
      long remainingMs = Duration.ofNanos(deadline - System.nanoTime()).toMillis();
      wait(Math.max(1L, remainingMs));
    }
    if (evalInvocations.size() != expected) {
      throw new AssertionError(
          "Timed out waiting for exactly %d Redis eval calls, observed %d"
              .formatted(expected, evalInvocations.size()));
    }

    long quietUntil = System.nanoTime() + quietPeriod.toNanos();
    int observedCount = evalInvocations.size();
    while (System.nanoTime() < quietUntil) {
      long remainingMs = Duration.ofNanos(quietUntil - System.nanoTime()).toMillis();
      wait(Math.max(1L, remainingMs));
      if (evalInvocations.size() != observedCount) {
        throw new AssertionError(
            "Expected exactly %d Redis eval calls but observed %d"
                .formatted(expected, evalInvocations.size()));
      }
    }

    return List.copyOf(evalInvocations);
  }

  public synchronized int scriptLoadCalls() {
    return scriptLoadCalls;
  }

  public synchronized List<EvalInvocation> evalInvocations() {
    return List.copyOf(evalInvocations);
  }

  public synchronized void reset() {
    evalInvocations.clear();
    scriptLoadCalls = 0;
  }
}
