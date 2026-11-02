// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.business.usecase.event;

import com.opencop.core.api.event.business.policy.EventAuthorizationPolicy;
import com.opencop.core.api.event.ports.business.usecase.RetrieveEventDetailHandler;
import com.opencop.core.api.event.ports.gateway.PostGisService;
import com.opencop.core.sdk.ports.gateway.audit.LastEventAtRecorder;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.model.opencop.OpenCopProperties;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class RetrieveEventDetailUsecase implements RetrieveEventDetailHandler {

  private final PostGisService service;
  private final EventAuthorizationPolicy authorizationPolicy;
  private final LastEventAtRecorder layerLastEventRecorder;

  RetrieveEventDetailUsecase(
      final PostGisService service,
      final EventAuthorizationPolicy authorizationPolicy,
      @Qualifier("layerLastEventRecorder") final LastEventAtRecorder layerLastEventRecorder) {
    this.service = service;
    this.authorizationPolicy = authorizationPolicy;
    this.layerLastEventRecorder = layerLastEventRecorder;
  }

  @Override
  public OpenCopProperties getDetails(OpenCopPrincipal principal, UUID eventId)
      throws PersistenceException {
    return authorizeAndRecord(principal, this.service.getDetails(eventId));
  }

  @Override
  public OpenCopProperties getLatestDetails(
      OpenCopPrincipal principal, UUID sourceId, String objectId) throws PersistenceException {
    return authorizeAndRecord(principal, this.service.getLatestDetails(sourceId, objectId));
  }

  /**
   * Authorizes the read, then records the visit — in that order, so an unauthorized probe leaves no
   * trace.
   *
   * <p>An event's source is a layer or a data source. Only layers carry activity; a data source id
   * matches no layer and is discarded downstream, which is why no type check happens here.
   *
   * <p>This is the layer usage signal in full: it measures an operator opening an object, not
   * inbound data, so a layer receiving events nobody opens will not advance. Clients poll while a
   * detail panel stays open, so the recorder coalesces rather than writing per request.
   */
  private OpenCopProperties authorizeAndRecord(
      OpenCopPrincipal principal, OpenCopProperties details) {
    authorizationPolicy.assertMember(principal, details.getSource(), details.getObjectId());
    layerLastEventRecorder.record(details.getSource(), Instant.now());
    return details;
  }
}
