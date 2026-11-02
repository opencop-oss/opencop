// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.business.usecase.event;

import com.opencop.core.api.event.business.policy.HistoricalTracksAuthorizationPolicy;
import com.opencop.core.api.event.business.policy.HistoricalTracksPreflightPlanner;
import com.opencop.core.api.event.business.policy.HistoricalTracksRequestPolicy;
import com.opencop.core.api.event.ports.business.usecase.SubmitHistoricalTracksHandler;
import com.opencop.core.api.event.ports.gateway.history.HistoricalTracksAdmissionGateway;
import com.opencop.core.api.event.technical.observability.history.HistoricalSubmissionTracing;
import com.opencop.core.api.general.ports.gateway.job.JobPublisher;
import com.opencop.core.sdk.model.history.HistoricalTracksAck;
import com.opencop.core.sdk.model.history.HistoricalTracksRequest;
import com.opencop.core.sdk.model.job.HistoricalTracksJobSubmission;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class SubmitHistoricalTracksUsecase implements SubmitHistoricalTracksHandler {

  private static final String PRINCIPAL_REQUIRED_MESSAGE = "principal";
  private static final String ACKNOWLEDGEMENT_REQUIRED_MESSAGE =
      "Historical tracks acknowledgement";

  private final HistoricalTracksRequestPolicy requestPolicy;
  private final HistoricalTracksAuthorizationPolicy authorizationPolicy;
  private final HistoricalTracksPreflightPlanner planner;
  private final HistoricalTracksAdmissionGateway admissionGateway;
  private final TransactionTemplate transactionTemplate;
  private final JobPublisher jobPublisher;
  private final HistoricalTracksJobSubmissionFactory submissionFactory;
  private final HistoricalTracksAckFactory ackFactory;
  private final HistoricalSubmissionTracing tracing;

  public SubmitHistoricalTracksUsecase(
      HistoricalTracksRequestPolicy requestPolicy,
      HistoricalTracksAuthorizationPolicy authorizationPolicy,
      HistoricalTracksPreflightPlanner planner,
      HistoricalTracksAdmissionGateway admissionGateway,
      PlatformTransactionManager transactionManager,
      JobPublisher jobPublisher,
      HistoricalTracksJobSubmissionFactory submissionFactory,
      HistoricalTracksAckFactory ackFactory,
      HistoricalSubmissionTracing tracing) {
    this.requestPolicy = requestPolicy;
    this.authorizationPolicy = authorizationPolicy;
    this.planner = planner;
    this.admissionGateway = admissionGateway;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
    this.transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    this.jobPublisher = jobPublisher;
    this.submissionFactory = submissionFactory;
    this.ackFactory = ackFactory;
    this.tracing = tracing;
  }

  @Override
  public HistoricalTracksAck submit(OpenCopPrincipal principal, HistoricalTracksRequest request) {
    return tracing.traceApiSubmit(
        traceSubjectType(request),
        () -> {
          Objects.requireNonNull(principal, PRINCIPAL_REQUIRED_MESSAGE);
          requestPolicy.validateShape(request);

          requestPolicy.rejectUnsupportedFeatures(request);

          var plan = planner.plan(request.start(), request.end());
          requestPolicy.validateGuardrails(request, plan);

          var authorizedSubjects =
              authorizationPolicy.authorizeSubjects(principal, request.sources());
          var requestId = UUID.randomUUID();
          var requestedAt = Instant.now();

          List<HistoricalTracksJobSubmission> submissions =
              List.of(
                  submissionFactory.create(
                      principal, request, plan, authorizedSubjects, requestId, requestedAt));

          transactionTemplate.executeWithoutResult(
              _ ->
                  jobPublisher.scheduleBatchAfterCommit(
                      submissions,
                      () ->
                          admissionGateway.requestCancellationForActiveJobs(
                              principal.internalUserId(), requestId)));

          var response = ackFactory.accepted(requestId, submissions);
          return Objects.requireNonNull(response, ACKNOWLEDGEMENT_REQUIRED_MESSAGE);
        });
  }

  private String traceSubjectType(HistoricalTracksRequest request) {
    if (request == null || request.sources().isEmpty()) {
      return null;
    }
    var firstSource = request.sources().getFirst();
    if (firstSource == null || firstSource.subjectType() == null) {
      return null;
    }
    return firstSource.subjectType().name();
  }
}
