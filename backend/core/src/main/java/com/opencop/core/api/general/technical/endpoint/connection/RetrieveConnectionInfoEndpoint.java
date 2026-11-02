// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.connection;

import com.opencop.core.api.general.ports.usecase.connection.retrieve.RetrieveConnection;
import com.opencop.core.api.general.technical.model.SourceCandidate;
import com.opencop.core.api.general.technical.service.LayerAuthorizationService;
import com.opencop.core.api.general.technical.service.SourceRelationshipAuthorizationService;
import com.opencop.core.sdk.model.connection.ConnectionInfo;
import com.opencop.core.sdk.model.visibility.RecordVisibility;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RetrieveConnectionInfoEndpoint {

  private final RetrieveConnection usecase;
  private final LayerAuthorizationService layerAuthorization;
  private final SourceRelationshipAuthorizationService sourceAuthorization;

  RetrieveConnectionInfoEndpoint(
      final RetrieveConnection usecase,
      final LayerAuthorizationService layerAuthorization,
      final SourceRelationshipAuthorizationService sourceAuthorization) {
    this.usecase = usecase;
    this.layerAuthorization = layerAuthorization;
    this.sourceAuthorization = sourceAuthorization;
  }

  @GetMapping(value = "/v1/connections/{sourceId}")
  @Operation(
      summary = "Retrieve a connection",
      description =
          """
          Returns the connection, including its integration settings. `settings` holds the \
          overrides the connection stores, or null when it overrides none. `effectiveSettings` \
          holds every declared setting as Core resolves it from those overrides and Core's \
          deployment defaults; it is not reported back by the running connection. It is null \
          when the connection has no integration, when this runtime does not register its schema \
          version, or when its stored overrides no longer resolve; empty when its schema declares \
          no settings. `settingsStatus` is NONE when there is nothing to resolve, RESOLVED, or \
          REJECTED with the reasons in `settingsViolations` as {code, message, target}. The \
          connection list and creation responses carry the same fields.""")
  public ResponseEntity<ConnectionInfo> retrieveSourceInfo(
      @AuthenticationPrincipal OpenCopPrincipal authentication, @PathVariable UUID sourceId) {
    // Knowing a source id is not authorization to read it: without this, any authenticated caller
    // could fetch any connection's configuration.
    sourceAuthorization.assertCanReadSource(authentication, sourceId);
    return ResponseEntity.status(HttpStatus.OK)
        .body(usecase.get(sourceId, authentication.internalUserId()));
  }

  @PreAuthorize("hasRole('admin')")
  @GetMapping(value = "/v1/admin/connections")
  public ResponseEntity<List<ConnectionInfo>> retrieveAllSourcesInfo(
      @AuthenticationPrincipal OpenCopPrincipal authentication,
      @RequestParam(defaultValue = "ACTIVE") RecordVisibility recordVisibility) {
    return ResponseEntity.status(HttpStatus.OK)
        .body(usecase.getAll(authentication.internalUserId(), recordVisibility));
  }

  @GetMapping(value = "/v1/layers/{layerId}/source-candidates")
  public List<SourceCandidate> retrieveSourceCandidatesForLayer(
      @AuthenticationPrincipal OpenCopPrincipal authentication, @PathVariable UUID layerId) {
    layerAuthorization.assertLayerRole(layerId, authentication, LayerRole.OWNER, LayerRole.ADMIN);
    return usecase.getAll(authentication.internalUserId()).stream()
        .map(source -> new SourceCandidate(source.id(), source.name()))
        .toList();
  }
}
