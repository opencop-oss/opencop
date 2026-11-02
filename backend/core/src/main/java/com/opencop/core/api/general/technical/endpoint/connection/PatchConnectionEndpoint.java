// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.connection;

import com.opencop.core.api.general.business.model.Caller;
import com.opencop.core.api.general.ports.usecase.connection.patch.PatchConnection;
import com.opencop.core.api.general.technical.model.ConnectionPatchRequest;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PatchConnectionEndpoint {

  private final PatchConnection usecase;

  PatchConnectionEndpoint(PatchConnection usecase) {
    this.usecase = usecase;
  }

  @PatchMapping("/v1/connections/{sourceId}")
  public ResponseEntity<Void> patchConnection(
      @AuthenticationPrincipal OpenCopPrincipal authentication,
      @PathVariable UUID sourceId,
      @RequestBody ConnectionPatchRequest request) {
    usecase.patch(
        sourceId,
        request.name(),
        request.defaultVisible(),
        request.access(),
        request.integration(),
        Caller.of(authentication));
    return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
  }
}
