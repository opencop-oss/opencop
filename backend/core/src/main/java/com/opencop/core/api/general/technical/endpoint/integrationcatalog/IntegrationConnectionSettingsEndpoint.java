// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.integrationcatalog;

import com.opencop.core.api.general.ports.usecase.integrationcatalog.DescribeIntegrationConnectionSettings;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves the connection settings form metadata for one integration schema version.
 *
 * <p>Guarded like connection setup: authentication only. It returns plugin-declared metadata and
 * deployment defaults, which are neither per-connection nor per-user.
 */
@RestController
@Tag(name = "Integrations")
public class IntegrationConnectionSettingsEndpoint {

  private final DescribeIntegrationConnectionSettings usecase;

  IntegrationConnectionSettingsEndpoint(DescribeIntegrationConnectionSettings usecase) {
    this.usecase = usecase;
  }

  @GetMapping("/v1/integrations/{integrationId}/versions/{schemaVersion}/connection/settings")
  @Operation(
      summary = "Describe the settings a connection to one integration schema version may override",
      description =
          """
          Returns the sections and fields of a connection's integration settings, each with its \
          kind, constraint and current deployment default, for a client with no hardcoded \
          knowledge of the integration.

          Read-only. An integration that declares no connection settings is described with an \
          empty `sections` list.""")
  @ApiResponse(
      responseCode = "200",
      description = "Sections and fields, in display order",
      content =
          @Content(
              schema = @Schema(implementation = IntegrationConnectionSettingsResponse.class),
              examples =
                  @ExampleObject(
                      name = "One section of every field kind",
                      value =
                          """
                          {
                            "integrationId": "8a1f0c4e-2d3b-4a5c-9e6f-7b8c9d0e1f20",
                            "schemaVersion": 2,
                            "sections": [
                              {
                                "name": "feed",
                                "label": "Feed",
                                "fields": [
                                  {
                                    "name": "enabled",
                                    "kind": "BOOLEAN",
                                    "label": "Send status reports",
                                    "default": true
                                  },
                                  {
                                    "name": "interval",
                                    "kind": "DURATION",
                                    "label": "Report every",
                                    "min": "PT5S",
                                    "max": "PT10M",
                                    "default": "PT30S"
                                  },
                                  {
                                    "name": "priority",
                                    "kind": "NUMBER",
                                    "label": "Priority",
                                    "min": 1,
                                    "max": 10,
                                    "integer": true,
                                    "default": 5
                                  },
                                  {
                                    "name": "stationName",
                                    "kind": "TEXT",
                                    "label": "Station name",
                                    "maxLength": 64,
                                    "default": "Station"
                                  },
                                  {
                                    "name": "colour",
                                    "kind": "CHOICE",
                                    "label": "Colour",
                                    "options": ["Blue", "Green", "Red"],
                                    "default": "Blue"
                                  }
                                ]
                              }
                            ]
                          }
                          """)))
  @ApiResponse(
      responseCode = "404",
      description = "No catalog row carries this identity and schema version",
      content =
          @Content(
              examples =
                  @ExampleObject(
                      name = "Unknown",
                      value = IntegrationConnectionSetupOpenApiExamples.NOT_FOUND_RESPONSE)))
  @ApiResponse(
      responseCode = "409",
      description =
          """
          The catalog carries this schema version, but its current state prevents describing \
          it: INTEGRATION_INACTIVE (an administrator deactivated it) or \
          INTEGRATION_UNAVAILABLE (this runtime registers no implementation for it).""",
      content =
          @Content(
              examples = {
                @ExampleObject(
                    name = "Inactive",
                    value = IntegrationConnectionSetupOpenApiExamples.INACTIVE_RESPONSE),
                @ExampleObject(
                    name = "Unavailable in this runtime",
                    value = IntegrationConnectionSetupOpenApiExamples.UNAVAILABLE_RESPONSE)
              }))
  public ResponseEntity<IntegrationConnectionSettingsResponse> describeConnectionSettings(
      @Parameter(description = "Integration identity") @PathVariable UUID integrationId,
      @Parameter(description = "Schema version; part of the integration's composite identity")
          @PathVariable
          int schemaVersion) {
    return ResponseEntity.ok(
        IntegrationConnectionSettingsResponse.from(
            integrationId, schemaVersion, usecase.describe(integrationId, schemaVersion)));
  }
}
