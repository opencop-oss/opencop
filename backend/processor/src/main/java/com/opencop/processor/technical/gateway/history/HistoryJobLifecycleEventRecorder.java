// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.springframework.stereotype.Component;

@Component
public class HistoryJobLifecycleEventRecorder {

  public void record(String eventName) {
    record(eventName, Context.current());
  }

  void record(String eventName, Context lifecycleContext) {
    try (Scope ignored = lifecycleContext.makeCurrent()) {
      Span currentSpan = Span.current();
      if (currentSpan.getSpanContext().isValid()) {
        currentSpan.addEvent(eventName);
      }
    }
  }
}
