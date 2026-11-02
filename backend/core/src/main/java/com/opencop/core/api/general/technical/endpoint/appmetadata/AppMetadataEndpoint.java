// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.appmetadata;

import static org.springframework.http.ResponseEntity.ok;

import com.opencop.core.api.general.business.model.runtimefeature.RuntimeFeatureMetadata;
import com.opencop.core.api.general.ports.usecase.runtimefeature.GetRuntimeFeatureMetadata;
import com.opencop.core.api.general.technical.endpoint.appmetadata.AppMetadataResponse.AdminNode;
import com.opencop.core.api.general.technical.endpoint.appmetadata.AppMetadataResponse.BffNode;
import com.opencop.core.api.general.technical.endpoint.appmetadata.AppMetadataResponse.FeatureNode;
import com.opencop.core.api.general.technical.endpoint.runtimefeature.RuntimeFeatureResponseMapper;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes app metadata for authenticated users, with build provenance only for global admins. */
@RestController
@Tag(name = "Metadata")
public class AppMetadataEndpoint {

  private final BuildMetadata buildMetadata;
  private final GetRuntimeFeatureMetadata getRuntimeFeatureMetadata;

  AppMetadataEndpoint(
      BuildMetadata buildMetadata, GetRuntimeFeatureMetadata getRuntimeFeatureMetadata) {
    this.buildMetadata = buildMetadata;
    this.getRuntimeFeatureMetadata = getRuntimeFeatureMetadata;
  }

  /**
   * GET /v1/app-metadata — authenticated by the global /v1/** rule; NOT @PreAuthorize-restricted.
   * Returns ResponseEntity with header Cache-Control: no-store. The public features list contains
   * caller-resolved availability for every registered runtime feature. The admin node is non-null
   * only when principal.isGlobalAdmin().
   */
  @GetMapping("/v1/app-metadata")
  public ResponseEntity<AppMetadataResponse> getAppMetadata(
      @AuthenticationPrincipal @Nullable OpenCopPrincipal principal) {
    if (principal == null) {
      throw new IllegalStateException("Authenticated principal is required for app metadata");
    }
    var metadata =
        getRuntimeFeatureMetadata.getFor(principal.internalUserId(), principal.isGlobalAdmin());
    var response =
        new AppMetadataResponse(
            buildMetadata.appVersion(),
            metadata.features().stream()
                .map(feature -> new FeatureNode(feature.name(), feature.enabled()))
                .toList(),
            adminNode(principal, metadata));

    return ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
  }

  private @Nullable AdminNode adminNode(
      OpenCopPrincipal principal, RuntimeFeatureMetadata metadata) {
    if (!principal.isGlobalAdmin()) {
      return null;
    }
    var bffNode =
        new BffNode(buildMetadata.revision(), buildMetadata.branch(), buildMetadata.buildDate());
    var runtimeFeatures =
        metadata.adminRuntimeFeatures().orElseThrow().stream()
            .map(RuntimeFeatureResponseMapper::toRecord)
            .toList();
    return new AdminNode(bffNode, runtimeFeatures);
  }
}
