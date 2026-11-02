// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.business.policy;

import static com.opencop.core.api.event.business.usecase.event.HistoricalErrorCode.HISTORICAL_SUBJECT_FORBIDDEN;

import com.opencop.core.sdk.model.history.HistoricalSubjectRequest;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.ports.gateway.iam.AccessRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class HistoricalTracksAuthorizationPolicy {

  private final AccessRepository<ServerRole> sourceAccess;
  private final AccessRepository<LayerRole> layerAccess;

  public HistoricalTracksAuthorizationPolicy(
      AccessRepository<ServerRole> sourceAccess, AccessRepository<LayerRole> layerAccess) {
    this.sourceAccess = sourceAccess;
    this.layerAccess = layerAccess;
  }

  // Returns the authorized subjects in request order as the internal request shape. The public
  // HistoricalSubjectAck is only constructed after submission, when a job id exists.
  public List<HistoricalSubjectRequest> authorizeSubjects(
      OpenCopPrincipal principal, List<HistoricalSubjectRequest> requestedSubjects) {
    if (principal.isGlobalAdmin()) {
      return List.copyOf(requestedSubjects);
    }

    for (var subject : requestedSubjects) {
      if (!hasAccess(principal.internalUserId(), subject)) {
        throw new GlobalOpenCopException(
            HISTORICAL_SUBJECT_FORBIDDEN.name(),
            "User is not authorized to access subject " + subject.subjectId(),
            HttpStatus.FORBIDDEN);
      }
    }
    return List.copyOf(requestedSubjects);
  }

  private boolean hasAccess(UUID userId, HistoricalSubjectRequest subject) {
    return switch (subject.subjectType()) {
      case SOURCE -> sourceAccess.userRole(userId, subject.subjectId()) != null;
      case LAYER -> layerAccess.userRole(userId, subject.subjectId()) != null;
    };
  }
}
