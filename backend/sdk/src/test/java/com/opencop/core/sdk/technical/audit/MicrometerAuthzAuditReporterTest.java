// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.audit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.opencop.core.sdk.ports.audit.AuthzDenialEvent;
import com.opencop.core.sdk.ports.audit.AuthzPermission;
import com.opencop.core.sdk.ports.audit.AuthzPrincipalType;
import com.opencop.core.sdk.ports.audit.AuthzReasonCode;
import com.opencop.core.sdk.ports.audit.AuthzResource;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class MicrometerAuthzAuditReporterTest {

  private static final UUID USER_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
  private static final UUID SOURCE_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
  private static final String ROUTE_ACTION = "GET /v1/events/{sourceId}/{objectId}/attachments";

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final MicrometerAuthzAuditReporter reporter = new MicrometerAuthzAuditReporter(registry);
  private Logger logger;
  private ListAppender<ILoggingEvent> appender;

  @BeforeEach
  void setUp() {
    logger = (Logger) LoggerFactory.getLogger(MicrometerAuthzAuditReporter.class);
    appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void tearDown() {
    RequestContextHolder.resetRequestAttributes();
    logger.detachAppender(appender);
    appender.stop();
    registry.close();
  }

  @Test
  void reportDenied_recordsStructuredLogAndFourTagCounter() {
    reporter.reportDenied(richEvent());

    var counter =
        registry
            .find(MicrometerAuthzAuditReporter.AUTHZ_DENIED_TOTAL)
            .tags(
                "action",
                ROUTE_ACTION,
                "resource_type",
                "EVENT",
                "reason_code",
                "MISSING_SOURCE_OR_LAYER_ROLE",
                "principal_type",
                "AUTHENTICATED")
            .counter();

    assertNotNull(counter);
    assertEquals(1.0, counter.count());
    assertEquals(
        Set.of("action", "resource_type", "reason_code", "principal_type"),
        counter.getId().getTags().stream()
            .map(io.micrometer.core.instrument.Tag::getKey)
            .collect(java.util.stream.Collectors.toSet()));

    assertEquals(1, appender.list.size());
    var message = appender.list.getFirst().getFormattedMessage();
    assertTrue(message.startsWith("AUTHZ_DENIED "));
    assertTrue(message.contains("principal_type=AUTHENTICATED"));
    assertTrue(message.contains("actor_user_id=" + USER_ID));
    assertTrue(message.contains("actor_email=user@example.com"));
    assertTrue(message.contains("resource_type=EVENT"));
    assertTrue(message.contains("resource_id=sourceId=" + SOURCE_ID + ",objectId=event-1"));
    assertTrue(message.contains("effective_permissions=NONE"));
    assertTrue(message.contains("required_permissions=SERVER_VIEWER,LAYER_MEMBER"));
  }

  @Test
  void reportDenied_sanitizesEmailAndResourceFieldsInLogs() {
    reporter.reportDenied(
        AuthzDenialEvent.rich(
            USER_ID,
            " user\n<tag>@example.com ",
            AuthzResource.event(SOURCE_ID, "object/with space?x=1"),
            "POST /v1/events/{sourceId}/{objectId}/attachments",
            AuthzReasonCode.MISSING_SOURCE_OR_LAYER_ROLE,
            List.of(AuthzPermission.NONE),
            List.of(AuthzPermission.SERVER_ADMIN, AuthzPermission.LAYER_EDITOR)));

    var message = appender.list.getFirst().getFormattedMessage();
    assertTrue(message.contains("actor_email=user_tag_@example.com"));
    assertTrue(
        message.contains("resource_id=sourceId=" + SOURCE_ID + ",objectId=object_with_space_x_1"));
    assertFalse(message.contains("\n"));
    assertFalse(message.contains("<tag>"));
  }

  @Test
  void clampedTag_returnsFallbackAndCountsFailureForUnsafeValues() {
    var allowed = Set.of("UNKNOWN");

    assertEquals(
        "UNKNOWN",
        reporter.clampedTag("action", "550e8400-e29b-41d4-a716-446655440120", allowed, "UNKNOWN"));
    assertEquals("UNKNOWN", reporter.clampedTag("action", " ", allowed, "UNKNOWN"));
    assertEquals("UNKNOWN", reporter.clampedTag("action", "39.9526", allowed, "UNKNOWN"));
    assertEquals("UNKNOWN", reporter.clampedTag("action", ROUTE_ACTION, allowed, "UNKNOWN"));

    var failureCounter =
        registry.find(MicrometerAuthzAuditReporter.AUTHZ_AUDIT_FAILURES_TOTAL).counter();
    assertNotNull(failureCounter);
    assertEquals(4.0, failureCounter.count());
    assertTrue(registry.find(MicrometerAuthzAuditReporter.AUTHZ_DENIED_TOTAL).meters().isEmpty());
  }

  @Test
  void reportDenied_marksRequestWhenRequestContextExists() {
    var request = new MockHttpServletRequest();
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

    reporter.reportDenied(richEvent());

    assertEquals(Boolean.TRUE, request.getAttribute(AuthzAuditRequestAttributes.REPORTED));
  }

  @Test
  void reportDenied_fallsBackToStashedEmailForIdOnlyEmitters() {
    // Usecases entering the policy with only a user id cannot supply an email
    // claim; the attribution filter stashed the sanitized email on the way in.
    var request = new MockHttpServletRequest();
    request.setAttribute(AuthzAuditRequestAttributes.ACTOR_EMAIL, "frank@localhost");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

    reporter.reportDenied(
        AuthzDenialEvent.rich(
            USER_ID,
            null,
            AuthzResource.event(SOURCE_ID, "event-1"),
            null,
            AuthzReasonCode.MISSING_SOURCE_OR_LAYER_ROLE,
            List.of(AuthzPermission.NONE),
            List.of(AuthzPermission.SERVER_VIEWER)));

    var message = appender.list.getFirst().getFormattedMessage();
    assertTrue(message.contains("actor_email=frank@localhost"));
  }

  @Test
  void reportDenied_survivesWithoutRequestContext() {
    RequestContextHolder.resetRequestAttributes();

    assertDoesNotThrow(() -> reporter.reportDenied(richEvent()));

    assertEquals(
        1.0, registry.find(MicrometerAuthzAuditReporter.AUTHZ_DENIED_TOTAL).counter().count());
  }

  @Test
  void reportDenied_neverThrowsOnRuntimeFailure() {
    assertDoesNotThrow(() -> reporter.reportDenied(null));

    var failureCounter =
        registry.find(MicrometerAuthzAuditReporter.AUTHZ_AUDIT_FAILURES_TOTAL).counter();
    assertNotNull(failureCounter);
    assertEquals(1.0, failureCounter.count());
  }

  @Test
  void reportDenied_genericEvent_logsUnknownSentinelsNotFabricatedRoles() {
    reporter.reportDenied(
        AuthzDenialEvent.generic(
            AuthzPrincipalType.UNKNOWN,
            null,
            null,
            AuthzResource.httpRequest("DELETE", "/v1/events/x/y/attachments/z"),
            null,
            AuthzReasonCode.UNATTRIBUTED));

    var message = appender.list.getFirst().getFormattedMessage();
    assertTrue(message.contains("actor_user_id=UNKNOWN"));
    assertTrue(message.contains("actor_email=UNKNOWN"));
    // Empty lists mean "not captured" — NONE is the rich sentinel for
    // "verified to hold no permissions" and must never appear on backstop lines.
    assertTrue(message.contains("effective_permissions=UNKNOWN"));
    assertTrue(message.contains("required_permissions=UNKNOWN"));
    assertFalse(message.contains("=NONE"));
  }

  @Test
  void reportDenied_whenDeniedCounterRegistrationThrows_logsFailureAndCountsIt() {
    var failingRegistry = new SimpleMeterRegistry();
    failingRegistry
        .config()
        .meterFilter(
            new MeterFilter() {
              @Override
              public Meter.Id map(Meter.Id id) {
                if (id.getName().equals(MicrometerAuthzAuditReporter.AUTHZ_DENIED_TOTAL)) {
                  throw new IllegalStateException("registry rejects denied counter");
                }
                return id;
              }
            });
    var failingReporter = new MicrometerAuthzAuditReporter(failingRegistry);

    assertDoesNotThrow(() -> failingReporter.reportDenied(richEvent()));

    assertEquals(
        1.0,
        failingRegistry
            .find(MicrometerAuthzAuditReporter.AUTHZ_AUDIT_FAILURES_TOTAL)
            .counter()
            .count());
    assertTrue(
        appender.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .anyMatch(message -> message.startsWith("AUTHZ_AUDIT_REPORT_FAILED")));
    failingRegistry.close();
  }

  @Test
  void reportDenied_whenFailureCounterAlsoThrows_stillNeverThrowsAndLogs() {
    var failingRegistry = new SimpleMeterRegistry();
    failingRegistry
        .config()
        .meterFilter(
            new MeterFilter() {
              @Override
              public Meter.Id map(Meter.Id id) {
                throw new IllegalStateException("registry rejects everything");
              }
            });
    var failingReporter = new MicrometerAuthzAuditReporter(failingRegistry);

    assertDoesNotThrow(() -> failingReporter.reportDenied(richEvent()));

    assertTrue(
        appender.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .anyMatch(message -> message.startsWith("AUTHZ_AUDIT_REPORT_FAILED")));
    failingRegistry.close();
  }

  private static AuthzDenialEvent richEvent() {
    return AuthzDenialEvent.rich(
        USER_ID,
        "user@example.com",
        AuthzResource.event(SOURCE_ID, "event-1"),
        "GET /v1/events/{sourceId}/{objectId}/attachments",
        AuthzReasonCode.MISSING_SOURCE_OR_LAYER_ROLE,
        List.of(AuthzPermission.NONE),
        List.of(AuthzPermission.SERVER_VIEWER, AuthzPermission.LAYER_MEMBER));
  }
}
