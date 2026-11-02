// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.observability.history;

import com.opencop.core.sdk.observability.BoundedMetricTagValue;

public final class HistoricalTelemetry {
  public static final String HISTORICAL_TRACER_NAME = "opencop.historical";
  public static final String SUBMISSION_TRACER_NAME = "opencop.historical-submission";
  public static final String PROCESSOR_CONSUMER_TRACER_NAME =
      "opencop.historical-processor-consumer";

  private HistoricalTelemetry() {}

  public enum Outcome implements BoundedMetricTagValue {
    SUCCESS("success"),
    ERROR("error"),
    UNDELIVERED("undelivered");

    private final String value;

    Outcome(String value) {
      this.value = value;
    }

    public String value() {
      return value;
    }
  }

  public enum SpanError {
    SEND_FAILED("send-failed"),
    TIMEOUT("timeout"),
    INTERRUPTED("interrupted"),
    CHUNK_COMPUTE_FAILED("chunk compute failed");

    private final String description;

    SpanError(String description) {
      this.description = description;
    }

    public String description() {
      return description;
    }
  }
}
