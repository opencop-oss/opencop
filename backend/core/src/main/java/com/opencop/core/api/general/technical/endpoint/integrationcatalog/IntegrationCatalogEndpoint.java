// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.integrationcatalog;

import com.opencop.core.api.general.ports.usecase.integrationcatalog.DiscoverIntegrations;
import com.opencop.core.sdk.business.model.integration.TransportProtocol;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lists the integration catalog for connection setup.
 *
 * <p>Open to any authenticated caller, the same guard as connection creation, granted by the global
 * {@code /v1/**} rule rather than a role: no operator role exists to gate it on. It exposes catalog
 * display metadata, which is neither per-connection nor per-user, so it has no per-resource
 * authorization surface of its own.
 */
@RestController
@Tag(name = "Integrations")
public class IntegrationCatalogEndpoint {

  private final DiscoverIntegrations usecase;

  IntegrationCatalogEndpoint(DiscoverIntegrations usecase) {
    this.usecase = usecase;
  }

  @GetMapping("/v1/integrations")
  @Operation(
      summary = "List the connection schema versions available to connection setup",
      description =
          """
          Returns one entry per version of a connection contract the catalog records, except \
          versions of an integration an operator deactivated, which are withheld entirely.

          Supply `protocol` to get the versions declaring a chosen transport, which is the first \
          call connection setup makes. Omit it to get the whole catalog, including versions no \
          loaded plugin provides here.

          A connection binds to one exact version, so each entry is one selectable choice and \
          display metadata repeats across the versions of one integration. An integration that \
          offers no connection contract has no entries.

          `available` reports whether a loaded plugin provides the version in the runtime \
          answering this request. It is computed per request and never stored, so the same catalog \
          row may be available on one node and not another. It means only that the version is \
          provided: it does not promise connection setup can be described for it, which the \
          connection-setup resource decides. Do not offer an unavailable version for a new \
          connection; it is still listed so a client can keep naming a version an existing \
          connection already binds to.

          Because a version no loaded plugin provides also declares no protocols, a \
          protocol-filtered response contains only available versions.

          Read-only: it mutates no catalog row and no connection state.""")
  @ApiResponse(
      responseCode = "200",
      description = "Matching versions, ordered by name then integration then schema version",
      content =
          @Content(
              array =
                  @ArraySchema(
                      schema = @Schema(implementation = IntegrationCatalogEntryResponse.class)),
              examples =
                  @ExampleObject(
                      name = "One available and one unavailable version",
                      value = IntegrationCatalogOpenApiExamples.CATALOG_RESPONSE)))
  @ApiResponse(
      responseCode = "400",
      description = "The requested protocol names no known transport protocol",
      content =
          @Content(
              examples =
                  @ExampleObject(
                      name = "Unknown protocol",
                      value = IntegrationCatalogOpenApiExamples.UNKNOWN_PROTOCOL_RESPONSE)))
  public ResponseEntity<List<IntegrationCatalogEntryResponse>> listIntegrations(
      @Parameter(
              description =
                  """
                  Transport protocol to filter by. Matched ignoring case and surrounding \
                  whitespace. Omit the parameter for the whole catalog; sending it empty is \
                  rejected rather than treated as omitted.""",
              schema = @Schema(implementation = TransportProtocol.class))
          @RequestParam(required = false)
          String protocol) {
    var entries = usecase.discover(protocol);
    return ResponseEntity.ok(entries.stream().map(IntegrationCatalogEntryResponse::from).toList());
  }
}
