// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.integrationcatalog;

import com.opencop.core.api.general.ports.usecase.integrationcatalog.ManageIntegrationActivation;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IntegrationActivationEndpoint {

  private final ManageIntegrationActivation usecase;

  IntegrationActivationEndpoint(ManageIntegrationActivation usecase) {
    this.usecase = usecase;
  }

  @PreAuthorize("hasRole('admin')")
  @PatchMapping("/v1/admin/integrations/{integrationId}")
  public ResponseEntity<Void> setActive(
      @PathVariable UUID integrationId, @RequestBody IntegrationActivationRequest request) {
    usecase.setActive(integrationId, request.active());
    return ResponseEntity.noContent().build();
  }
}
