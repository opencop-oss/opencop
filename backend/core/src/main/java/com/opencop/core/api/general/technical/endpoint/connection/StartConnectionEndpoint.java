// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.connection;

import com.opencop.core.api.general.business.model.Caller;
import com.opencop.core.api.general.ports.usecase.connection.start.StartConnection;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StartConnectionEndpoint {

  private final StartConnection usecase;

  StartConnectionEndpoint(final StartConnection usecase) {
    this.usecase = usecase;
  }

  @PostMapping("/v1/connections/{sourceId}/start")
  public ResponseEntity<Void> reconnectConnection(
      @AuthenticationPrincipal final OpenCopPrincipal authentication, @PathVariable UUID sourceId) {
    usecase.start(sourceId, Caller.of(authentication));
    return ResponseEntity.status(HttpStatus.OK).build();
  }
}
