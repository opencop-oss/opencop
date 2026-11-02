// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.business.usecase.event;

import static com.opencop.core.api.event.ports.business.usecase.EventExceptionCodes.INTERNAL_ERROR;

import com.opencop.core.api.event.business.model.EventHistoryResponse;
import com.opencop.core.api.event.business.policy.EventAuthorizationPolicy;
import com.opencop.core.api.event.ports.business.usecase.RetrieveEventHistoryHandler;
import com.opencop.core.api.event.ports.gateway.PostGisService;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class RetrieveEventHistoryUsecase implements RetrieveEventHistoryHandler {

  private static final int MAX_ALLOWED_POINTS = 2000;

  private final PostGisService service;
  private final EventAuthorizationPolicy authorizationPolicy;

  RetrieveEventHistoryUsecase(
      final PostGisService service, final EventAuthorizationPolicy authorizationPolicy) {
    this.service = service;
    this.authorizationPolicy = authorizationPolicy;
  }

  @Override
  public EventHistoryResponse getHistory(
      OpenCopPrincipal principal,
      UUID sourceId,
      String objectId,
      Instant from,
      Instant to,
      int maxPoints,
      boolean asLine) {
    authorizationPolicy.assertMember(principal, sourceId, objectId);

    var boundedMaxPoints = Math.min(maxPoints, MAX_ALLOWED_POINTS);
    try {
      return asLine
          ? this.service.getEventHistoryAsLine(sourceId, objectId, from, to, boundedMaxPoints)
          : this.service.getEventHistoryBucketed(sourceId, objectId, from, to, boundedMaxPoints);
    } catch (PersistenceException e) {
      throw new GlobalOpenCopException(
          INTERNAL_ERROR.code(), e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }
}
