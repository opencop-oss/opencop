// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.media;

import com.opencop.core.api.general.business.usecase.media.StreamAuthorizationDecision;
import com.opencop.core.api.general.business.usecase.media.StreamAuthorizationReason;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class StreamAuthorizationTelemetry {

  static final String STREAM_AUTHZ_TOTAL = "opencop_stream_authz_total";

  private static final Logger log = LoggerFactory.getLogger(StreamAuthorizationTelemetry.class);

  private final MeterRegistry meterRegistry;

  public StreamAuthorizationTelemetry(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  public void recordDecision(StreamAuthorizationDecision decision) {
    recordAuthorization(decision.allowed(), decision.reason());
  }

  public void recordDenial(StreamAuthorizationReason reason) {
    recordAuthorization(false, reason);
  }

  private void recordAuthorization(boolean allowed, StreamAuthorizationReason reason) {
    var outcome = allowed ? "allowed" : "denied";
    var reasonCode = reason.code();

    meterRegistry.counter(STREAM_AUTHZ_TOTAL, "outcome", outcome, "reason", reasonCode).increment();

    if (log.isInfoEnabled()) {
      log.info("STREAM_AUTHZ outcome={} reason={}", outcome, reasonCode);
    }
  }
}
