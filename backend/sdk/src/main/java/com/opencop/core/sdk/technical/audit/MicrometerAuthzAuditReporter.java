// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.audit;

import com.opencop.core.sdk.observability.BoundedMetricTagGuard;
import com.opencop.core.sdk.ports.audit.AuthzAuditReporting;
import com.opencop.core.sdk.ports.audit.AuthzDenialEvent;
import com.opencop.core.sdk.ports.audit.AuthzPermission;
import com.opencop.core.sdk.ports.audit.AuthzPrincipalType;
import com.opencop.core.sdk.ports.audit.AuthzReasonCode;
import com.opencop.core.sdk.ports.audit.AuthzResourceType;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

public class MicrometerAuthzAuditReporter implements AuthzAuditReporting {

  static final String AUTHZ_DENIED_TOTAL = "opencop_authz_denied_total";
  static final String AUTHZ_AUDIT_FAILURES_TOTAL = "opencop_authz_audit_failures_total";

  private static final Logger log = LoggerFactory.getLogger(MicrometerAuthzAuditReporter.class);
  private static final Set<String> RESOURCE_TYPE_VALUES =
      BoundedMetricTagGuard.allowedValues(AuthzResourceType.values());
  private static final Set<String> REASON_CODE_VALUES =
      BoundedMetricTagGuard.allowedValues(AuthzReasonCode.values());
  private static final Set<String> PRINCIPAL_TYPE_VALUES =
      BoundedMetricTagGuard.allowedValues(AuthzPrincipalType.values());

  private final MeterRegistry meterRegistry;

  public MicrometerAuthzAuditReporter(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  @Override
  public void reportDenied(AuthzDenialEvent event) {
    try {
      // Marked before emission on purpose: if emission fails the backstop must
      // not double-report the denial. The bias is under-count over
      // double-count; the failure counter records the gap.
      markRequestReported();
      var action = resolveAction(event);
      log.warn(
          "AUTHZ_DENIED principal_type={} actor_user_id={} actor_email={} resource_type={} resource_id={} action={} reason_code={} effective_permissions={} required_permissions={}",
          event.principalType().value(),
          event.actorUserId() == null ? "UNKNOWN" : event.actorUserId().toString(),
          resolveActorEmail(event),
          event.resource().type().value(),
          event.resource().id(),
          action,
          event.reasonCode().value(),
          renderPermissions(event.effectivePermissions()),
          renderPermissions(event.requiredPermissions()));
      meterRegistry
          .counter(
              AUTHZ_DENIED_TOTAL,
              // Action is the request line (method + route template):
              // server-controlled, bounded by the deployed route table, and
              // sanitized/capped at derivation — the same bounding model as
              // http.server.requests' route tag, so no enum allowlist applies.
              "action",
              action,
              "resource_type",
              clampedTag(
                  "resource_type",
                  event.resource().type().value(),
                  RESOURCE_TYPE_VALUES,
                  AuthzResourceType.UNKNOWN.value()),
              "reason_code",
              clampedTag(
                  "reason_code",
                  event.reasonCode().value(),
                  REASON_CODE_VALUES,
                  AuthzReasonCode.UNATTRIBUTED.value()),
              "principal_type",
              clampedTag(
                  "principal_type",
                  event.principalType().value(),
                  PRINCIPAL_TYPE_VALUES,
                  AuthzPrincipalType.UNKNOWN.value()))
          .increment();
    } catch (RuntimeException e) {
      incrementFailureCounterSafely(e);
    }
  }

  String clampedTag(String tagKey, String value, Set<String> allowedValues, String fallback) {
    try {
      return BoundedMetricTagGuard.requireAllowed(tagKey, value, allowedValues);
    } catch (IllegalArgumentException e) {
      incrementFailureCounterSafely(e);
      return fallback;
    }
  }

  // Rich emitters run in-request and pass no action; derive the request line
  // here while RequestContextHolder is live. The backstop derives from its
  // own request instead (the holder is already reset at its position) and
  // passes the value through the event.
  private static String resolveAction(AuthzDenialEvent event) {
    if (!"UNKNOWN".equals(event.action())) {
      return event.action();
    }
    var derived = AuthzRequestActions.currentRouteAction();
    return derived == null || derived.isBlank() ? "UNKNOWN" : derived;
  }

  // Same emitter-derivation pattern for the log-only email: usecases entering
  // the policy with only a user id cannot supply a claim, but the in-chain
  // attribution filter stashed the (sanitized) email on the request.
  private static String resolveActorEmail(AuthzDenialEvent event) {
    if (!"UNKNOWN".equals(event.actorEmail())) {
      return event.actorEmail();
    }
    var requestAttributes = RequestContextHolder.getRequestAttributes();
    if (requestAttributes == null) {
      return event.actorEmail();
    }
    var stashed =
        requestAttributes.getAttribute(
            AuthzAuditRequestAttributes.ACTOR_EMAIL, RequestAttributes.SCOPE_REQUEST);
    return stashed instanceof String email && !email.isBlank() ? email : event.actorEmail();
  }

  private void markRequestReported() {
    var requestAttributes = RequestContextHolder.getRequestAttributes();
    if (requestAttributes != null) {
      requestAttributes.setAttribute(
          AuthzAuditRequestAttributes.REPORTED, Boolean.TRUE, RequestAttributes.SCOPE_REQUEST);
    }
  }

  private void incrementFailureCounterSafely(RuntimeException e) {
    // Always log: the failure counter alone carries no diagnostic context, and
    // a persistent failure would otherwise be an unexplained counter exactly
    // when the reconciliation anchor diverges.
    log.warn("AUTHZ_AUDIT_REPORT_FAILED exception_class={}", e.getClass().getSimpleName());
    try {
      meterRegistry.counter(AUTHZ_AUDIT_FAILURES_TOTAL).increment();
    } catch (RuntimeException ignored) {
      // Reporting must never throw; the warn above is the remaining signal.
    }
  }

  private static String renderPermissions(List<AuthzPermission> permissions) {
    // Empty means "not captured" (generic/backstop events). NONE is the rich
    // sentinel for "verified to hold no permissions" and must not be
    // fabricated here.
    if (permissions == null || permissions.isEmpty()) {
      return "UNKNOWN";
    }
    return permissions.stream().map(AuthzPermission::value).collect(Collectors.joining(","));
  }
}
