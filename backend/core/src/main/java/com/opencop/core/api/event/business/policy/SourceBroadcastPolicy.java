// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.business.policy;

import static com.opencop.core.api.event.ports.business.usecase.EventExceptionCodes.UNAUTHORIZED;

import com.opencop.core.api.event.ports.business.usecase.OpencopEventException;
import com.opencop.core.sdk.ports.gateway.iam.SourceAccessRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class SourceBroadcastPolicy {

  private final SourceAccessRepository sourceAccessRepository;

  public SourceBroadcastPolicy(SourceAccessRepository sourceAccessRepository) {
    this.sourceAccessRepository = sourceAccessRepository;
  }

  public void assertSourceMutator(UUID userId, UUID sourceId) {
    ServerRole role = sourceAccessRepository.userRole(userId, sourceId);
    if (role == null || role.isLowerThan(ServerRole.ADMIN)) {
      throw new OpencopEventException(UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
    }
  }
}
