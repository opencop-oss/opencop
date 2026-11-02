// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.watchbox.business.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import com.opencop.core.sdk.ports.gateway.api.WatchboxDetectionLivenessReader;
import com.opencop.core.sdk.ports.gateway.api.WatchboxDetectionLivenessReader.DetectionLiveness;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class WatchboxDetectionLivenessServiceTest {

  private static final String SENSITIVE_DETAIL = "Failing row contains (private heartbeat payload)";

  private final Logger logger =
      (Logger) LoggerFactory.getLogger(WatchboxDetectionLivenessService.class);
  private Level originalLevel;
  private ListAppender<ILoggingEvent> appender;

  @BeforeEach
  void setUpAppender() {
    originalLevel = logger.getLevel();
    logger.setLevel(Level.DEBUG);
    appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void tearDownAppender() {
    logger.detachAppender(appender);
    appender.stop();
    logger.setLevel(originalLevel);
  }

  @Test
  void detectionLiveness_whenReadFails_warnsSafelyAndRetainsDebugStack() {
    var reader = mock(WatchboxDetectionLivenessReader.class);
    var failure = new IllegalStateException(SENSITIVE_DETAIL);
    when(reader.detectionLiveness(any())).thenThrow(failure);
    var service = new WatchboxDetectionLivenessService(reader, Duration.ofSeconds(30));

    assertThat(service.detectionLiveness()).isEqualTo(DetectionLiveness.NOT_RUNNING);

    assertThat(appender.list).hasSize(2);
    var warning = eventAt(Level.WARN);
    assertThat(warning.getFormattedMessage())
        .contains(
            "WATCHBOX_DETECTION_LIVENESS_READ_FAILED",
            "reason=POSTGRES_READ_FAILED",
            "exception_class=IllegalStateException")
        .doesNotContain(SENSITIVE_DETAIL, "private heartbeat payload");
    assertThat(warning.getThrowableProxy()).isNull();

    var debug = eventAt(Level.DEBUG);
    assertThat(debug.getFormattedMessage())
        .isEqualTo("WATCHBOX_DETECTION_LIVENESS_READ_FAILED detail=stack_trace")
        .doesNotContain(SENSITIVE_DETAIL);
    assertThat(debug.getThrowableProxy()).isNotNull();
    assertThat(ThrowableProxyUtil.asString(debug.getThrowableProxy())).contains(SENSITIVE_DETAIL);
  }

  private ILoggingEvent eventAt(Level level) {
    return appender.list.stream()
        .filter(event -> event.getLevel() == level)
        .findFirst()
        .orElseThrow(() -> new AssertionError("Missing " + level + " log event"));
  }
}
