// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.usecase.history;

import com.opencop.core.sdk.observability.history.HistoricalTelemetry.Outcome;
import com.opencop.core.sdk.observability.history.HistoricalTraceContext;
import com.opencop.processor.business.model.history.HistoricalExecutionContext;
import com.opencop.processor.business.model.history.HistoricalSubjectExecutionMeta;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlan;
import com.opencop.processor.business.policy.history.HistoricalChunkCacheAnalyzer;
import com.opencop.processor.ports.technical.gateway.history.HistoricalChunkStateGateway;
import com.opencop.processor.ports.technical.gateway.history.HistoricalJobStateGateway;
import com.opencop.processor.ports.technical.gateway.history.HistoricalPlanGateway;
import com.opencop.processor.ports.technical.gateway.history.HistoryEventPublisher;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class HistoricalSubjectLaneJobHandler {

  static final String PROCESSOR_PLANNING_SPAN = "history.processor.planning";
  private static final Logger log = LoggerFactory.getLogger(HistoricalSubjectLaneJobHandler.class);

  private final HistoricalJobStateGateway jobStateGateway;
  private final HistoricalChunkCacheAnalyzer cacheAnalyzer;
  private final HistoricalChunkStateGateway chunkStateGateway;
  private final HistoricalPlanGateway planGateway;
  private final HistoryEventPublisher historyEventPublisher;
  private final HistoricalChunkExecutionCoordinator chunkExecutionCoordinator;
  private final Tracer tracer;

  public HistoricalSubjectLaneJobHandler(
      HistoricalJobStateGateway jobStateGateway,
      HistoricalChunkCacheAnalyzer cacheAnalyzer,
      HistoricalChunkStateGateway chunkStateGateway,
      HistoricalPlanGateway planGateway,
      HistoryEventPublisher historyEventPublisher,
      HistoricalChunkExecutionCoordinator chunkExecutionCoordinator,
      Tracer tracer) {
    this.jobStateGateway = jobStateGateway;
    this.cacheAnalyzer = cacheAnalyzer;
    this.chunkStateGateway = chunkStateGateway;
    this.planGateway = planGateway;
    this.historyEventPublisher = historyEventPublisher;
    this.chunkExecutionCoordinator = chunkExecutionCoordinator;
    this.tracer = tracer;
  }

  public void handle(HistoricalSubjectExecutionMeta laneMetadata) {
    String workerId = Thread.currentThread().getName();
    boolean claimed = jobStateGateway.markRunning(laneMetadata.laneJobId(), workerId);
    if (!claimed) {
      throw new IllegalStateException(
          "Lost race on markRunning for laneJobId=" + laneMetadata.laneJobId());
    }
    log.info(
        "Received historical subject lane {} for request {} on {} {} with resolution {} and {} planned chunks",
        laneMetadata.laneJobId(),
        laneMetadata.requestId(),
        laneMetadata.subjectType(),
        laneMetadata.subjectId(),
        laneMetadata.resolution(),
        laneMetadata.plannedChunks());
    boolean planning = jobStateGateway.markPlanning(laneMetadata.laneJobId());
    if (!planning) {
      throw new IllegalStateException(
          "Lost race on markPlanning for jobId=" + laneMetadata.laneJobId());
    }
    if (Thread.currentThread().isInterrupted()) {
      throw new IllegalStateException("Historical job interrupted before execution");
    }

    HistoricalExecutionContext executionContext = HistoricalExecutionContext.from(laneMetadata);
    Span planningSpan = tracer.spanBuilder(PROCESSOR_PLANNING_SPAN).startSpan();
    planningSpan.setAllAttributes(
        HistoricalTraceContext.spanAttributes(spanAttributes(executionContext)));
    HistoricalChunkPlan analyzedPlan;
    try {
      HistoricalChunkPlan plan =
          HistoricalChunkPlan.forSubject(laneMetadata, laneMetadata.windowPlan());
      analyzedPlan = cacheAnalyzer.analyze(plan, laneMetadata);
      planningSpan.setStatus(StatusCode.OK, Outcome.SUCCESS.value());
    } catch (RuntimeException e) {
      planningSpan.setStatus(StatusCode.ERROR, Outcome.ERROR.value());
      planningSpan.recordException(e);
      throw e;
    } finally {
      planningSpan.end();
    }

    chunkStateGateway.initialize(analyzedPlan);
    planGateway.save(analyzedPlan);
    historyEventPublisher.publishPlan(analyzedPlan, executionContext);
    jobStateGateway.markPlanned(laneMetadata.laneJobId());
    chunkExecutionCoordinator.execute(laneMetadata, analyzedPlan);
  }

  private static Map<String, String> spanAttributes(HistoricalExecutionContext executionContext) {
    Map<String, String> attributes = new LinkedHashMap<>();
    attributes.put(
        HistoricalTraceContext.ATTRIBUTE_REQUEST_ID, executionContext.requestId().toString());
    attributes.put(HistoricalTraceContext.ATTRIBUTE_JOB_ID, executionContext.jobId().toString());
    attributes.put(
        HistoricalTraceContext.ATTRIBUTE_SUBJECT_TYPE, executionContext.subjectType().name());
    return attributes;
  }
}
