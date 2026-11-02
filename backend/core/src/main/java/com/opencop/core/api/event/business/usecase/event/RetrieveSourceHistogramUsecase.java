// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.business.usecase.event;

import static com.opencop.core.api.event.ports.business.usecase.EventExceptionCodes.INTERNAL_ERROR;

import com.opencop.core.api.event.business.model.BoundingBox;
import com.opencop.core.api.event.business.model.SourceHistogramResponse;
import com.opencop.core.api.event.business.policy.EventAuthorizationPolicy;
import com.opencop.core.api.event.business.policy.HistoricalTracksPreflightPlanner;
import com.opencop.core.api.event.business.policy.HistoricalTracksRequestPolicy;
import com.opencop.core.api.event.ports.business.usecase.RetrieveSourceHistogramHandler;
import com.opencop.core.api.event.ports.gateway.PostGisService;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class RetrieveSourceHistogramUsecase implements RetrieveSourceHistogramHandler {

  private final PostGisService service;
  private final EventAuthorizationPolicy authorizationPolicy;
  private final HistoricalTracksPreflightPlanner planner;
  private final HistoricalTracksRequestPolicy requestPolicy;

  RetrieveSourceHistogramUsecase(
      final PostGisService service,
      final EventAuthorizationPolicy authorizationPolicy,
      final HistoricalTracksPreflightPlanner planner,
      final HistoricalTracksRequestPolicy requestPolicy) {
    this.service = service;
    this.authorizationPolicy = authorizationPolicy;
    this.planner = planner;
    this.requestPolicy = requestPolicy;
  }

  @Override
  public SourceHistogramResponse getHistogram(
      OpenCopPrincipal principal, UUID sourceId, Instant from, Instant to, BoundingBox bbox) {
    if (!from.isBefore(to)) {
      throw new GlobalOpenCopException("'from' must be before 'to'", HttpStatus.BAD_REQUEST);
    }

    var plan = planner.plan(from, to);
    requestPolicy.validateWindowGuardrails(from, to, plan.chunkCount());

    authorizationPolicy.assertSourceMember(principal, sourceId);

    try {
      int bars = Math.toIntExact(plan.chunkCount());
      var response =
          this.service.getSourceHistogram(
              sourceId,
              plan.effectiveFrom(),
              plan.effectiveTo(),
              bars,
              plan.resolution().chunkSize(),
              bbox);
      return new SourceHistogramResponse(
          from,
          to,
          plan.effectiveFrom(),
          plan.effectiveTo(),
          plan.resolution(),
          bars,
          plan.resolution().chunkSize().toMillis(),
          response.buckets());
    } catch (PersistenceException e) {
      throw new GlobalOpenCopException(
          INTERNAL_ERROR.code(), e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }
}
