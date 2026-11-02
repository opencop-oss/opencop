// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.integrationcatalog;

import com.opencop.core.api.general.ports.usecase.integrationcatalog.DescribeIntegrationConnectionSetup;
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
 * Serves the connection-setup form metadata for one integration schema version.
 *
 * <p>Operator-facing, so it carries the same guard as connection creation: authentication only. It
 * returns plugin-declared form metadata and the identifiers of permitted mapper/framing choices,
 * which are neither per-connection nor per-user, so it has no per-resource authorization surface of
 * its own. The route carries the schema version because the catalog key is composite.
 */
@RestController
@Tag(name = "Integrations")
public class IntegrationConnectionSetupEndpoint {

  private final DescribeIntegrationConnectionSetup usecase;

  IntegrationConnectionSetupEndpoint(DescribeIntegrationConnectionSetup usecase) {
    this.usecase = usecase;
  }

  @GetMapping("/v1/integrations/{integrationId}/versions/{schemaVersion}/connection/setup")
  @Operation(
      summary = "Describe how to set up a connection to one integration schema version",
      description =
          """
          Returns the input a client needs to render a connection-setup form for an integration \
          it has no hardcoded knowledge of, for each protocol it offers: every extractor this \
          runtime registers, each with its declared fields and accepted formats, plus the \
          ingress rules and, for a protocol that writes, the egress rule the integration \
          permits.

          Pick the form component for each field from the field's `kind`, and submit a mapper \
          choice, and for TCP a framing, that one of the protocol's `ingress` rules permits.

          Read-only: it mutates no catalog row, connection, or registry state. The declared \
          fields are a rendering hint, not a validation contract. The integration's own \
          validation is the sole authority on whether submitted input is acceptable.

          A protocol the integration declares for its mapping rule alone, with no extractor to \
          read input through, is left out. An integration that offers no protocol is reported \
          explicitly rather than answered with an empty success.""")
  @ApiResponse(
      responseCode = "200",
      description = "Extractors and permitted mapper choices, per protocol",
      content =
          @Content(
              schema = @Schema(implementation = IntegrationConnectionSetupResponse.class),
              examples =
                  @ExampleObject(
                      name = "TCP with an egress rule, UDP and RTSP",
                      value = IntegrationConnectionSetupOpenApiExamples.CONNECTION_SETUP_RESPONSE)))
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
          it. The response code distinguishes the three cases: \
          INTEGRATION_INACTIVE (an administrator deactivated it), \
          INTEGRATION_UNAVAILABLE (this runtime registers no implementation for it), and \
          INTEGRATION_PROTOCOL_UNSUPPORTED (it offers no protocol to set a connection up over).""",
      content =
          @Content(
              examples = {
                @ExampleObject(
                    name = "Inactive",
                    value = IntegrationConnectionSetupOpenApiExamples.INACTIVE_RESPONSE),
                @ExampleObject(
                    name = "Unavailable in this runtime",
                    value = IntegrationConnectionSetupOpenApiExamples.UNAVAILABLE_RESPONSE),
                @ExampleObject(
                    name = "No connection setup",
                    value = IntegrationConnectionSetupOpenApiExamples.PROTOCOL_UNSUPPORTED_RESPONSE)
              }))
  public ResponseEntity<IntegrationConnectionSetupResponse> describeConnectionSetup(
      @Parameter(description = "Integration identity") @PathVariable UUID integrationId,
      @Parameter(description = "Schema version; part of the integration's composite identity")
          @PathVariable
          int schemaVersion) {
    return ResponseEntity.ok(
        IntegrationConnectionSetupResponse.from(usecase.describe(integrationId, schemaVersion)));
  }
}
