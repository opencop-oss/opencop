// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.connection;

import com.opencop.core.api.general.ports.gateway.authorization.ConnectionAuthorization;
import com.opencop.core.api.general.ports.usecase.connection.restart.RestartConnection;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RestartConnectionEndpoint {

  private final RestartConnection usecase;
  private final ConnectionAuthorization authorization;

  RestartConnectionEndpoint(RestartConnection usecase, ConnectionAuthorization authorization) {
    this.usecase = usecase;
    this.authorization = authorization;
  }

  @PostMapping("/v1/connections/{sourceId}/restart")
  public ResponseEntity<Void> restartConnection(
      @AuthenticationPrincipal OpenCopPrincipal authentication, @PathVariable UUID sourceId) {
    authorization.assertDirectOwnerAdminOrGlobalAdmin(
        sourceId, authentication.internalUserId(), authentication.isGlobalAdmin());
    usecase.restart(sourceId, authentication.internalUserId());
    return ResponseEntity.status(HttpStatus.ACCEPTED).build();
  }
}
