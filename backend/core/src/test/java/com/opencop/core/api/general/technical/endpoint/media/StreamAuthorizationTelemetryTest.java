// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.opencop.core.api.general.business.usecase.media.StreamAuthorizationDecision;
import com.opencop.core.api.general.business.usecase.media.StreamAuthorizationReason;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class StreamAuthorizationTelemetryTest {

  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440142");

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final StreamAuthorizationTelemetry telemetry = new StreamAuthorizationTelemetry(registry);
  private Logger logger;
  private ListAppender<ILoggingEvent> appender;

  @BeforeEach
  void setUp() {
    logger = (Logger) LoggerFactory.getLogger(StreamAuthorizationTelemetry.class);
    appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void tearDown() {
    logger.detachAppender(appender);
    appender.stop();
    registry.close();
  }

  @Test
  void record_allowedDecisionUsesLowCardinalityTagsAndStructuredLog() {
    telemetry.recordDecision(StreamAuthorizationDecision.allow());

    var counter =
        registry
            .find(StreamAuthorizationTelemetry.STREAM_AUTHZ_TOTAL)
            .tags("outcome", "allowed", "reason", "allowed")
            .counter();

    assertNotNull(counter);
    assertEquals(1.0d, counter.count());
    assertEquals(
        Set.of("outcome", "reason"),
        counter.getId().getTags().stream().map(Tag::getKey).collect(Collectors.toSet()));

    var message = appender.list.getFirst().getFormattedMessage();
    assertTrue(message.startsWith("STREAM_AUTHZ "));
    assertTrue(message.contains("outcome=allowed"));
    assertTrue(message.contains("reason=allowed"));
    assertNoSensitiveStreamMaterial(message);
  }

  @Test
  void record_allowedDecisionLogDoesNotIncludeIdentifiersPathsUrlsOrCredentials() {
    telemetry.recordDecision(StreamAuthorizationDecision.allow());

    var message = appender.list.getFirst().getFormattedMessage();
    assertNoSensitiveStreamMaterial(message);
    assertFalse(message.contains("actor_user_id="));
    assertFalse(message.contains("att_"));
    assertFalse(message.contains("rtsp://"));
    assertFalse(message.contains("/streams/"));
    assertFalse(message.contains("secret"));
  }

  @Test
  void recordDenied_unauthenticatedLogsOnlyOutcomeAndReason() {
    telemetry.recordDenial(StreamAuthorizationReason.UNAUTHENTICATED);

    var counter =
        registry
            .find(StreamAuthorizationTelemetry.STREAM_AUTHZ_TOTAL)
            .tags("outcome", "denied", "reason", "unauthenticated")
            .counter();

    assertNotNull(counter);
    assertEquals(1.0d, counter.count());
    var message = appender.list.getFirst().getFormattedMessage();
    assertTrue(message.contains("outcome=denied"));
    assertTrue(message.contains("reason=unauthenticated"));
    assertNoSensitiveStreamMaterial(message);
  }

  private static void assertNoSensitiveStreamMaterial(String message) {
    assertFalse(message.contains(USER_ID.toString()));
    assertFalse(message.contains("actor_user_id="));
    assertFalse(message.contains("att_"));
    assertFalse(message.contains("/streams/"));
    assertFalse(message.contains("rtsp://"));
    assertFalse(message.contains("secret"));
  }
}
