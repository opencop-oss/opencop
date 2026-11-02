// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.connection.configuration;

import com.opencop.core.api.general.business.model.Caller;
import com.opencop.core.api.general.ports.usecase.connection.configuration.ConfigureConnection;
import com.opencop.core.sdk.model.connection.ConnectionInfo;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Changes what a running connection applies without reconnecting it. */
@RestController
public class ConfigureConnectionEndpoint {

  private final ConfigureConnectionRequestReader reader;
  private final ConfigureConnection configureConnection;

  ConfigureConnectionEndpoint(
      ConfigureConnectionRequestReader reader, ConfigureConnection configureConnection) {
    this.reader = reader;
    this.configureConnection = configureConnection;
  }

  @Operation(
      summary = "Change a connection's live configuration",
      requestBody =
          @io.swagger.v3.oas.annotations.parameters.RequestBody(
              required = true,
              content =
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON_VALUE,
                      schema = @Schema(implementation = ConfigureConnectionRequest.class))),
      description =
          "Applied to the running connection without reconnecting it. A new ingress mapper takes"
              + " effect within a minute. Rejected with NO_FIELDS_TO_UPDATE when nothing is sent,"
              + " with UNKNOWN_REQUEST_FIELD for a field it does not define, with the creation codes"
              + " when a selection or a setting is invalid, and with INTEGRATION_INACTIVE or"
              + " INTEGRATION_UNAVAILABLE (409) when the connection's integration can no longer be"
              + " used, except for a request that only clears the settings.")
  @PatchMapping(
      value = "/v2/connections/{sourceId}/configuration",
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<ConnectionInfo> configure(
      @AuthenticationPrincipal OpenCopPrincipal principal,
      @PathVariable UUID sourceId,
      @RequestBody String body) {
    return ResponseEntity.ok(
        configureConnection.configure(sourceId, reader.read(body), Caller.of(principal)));
  }
}
