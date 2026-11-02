// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.connection.create;

import com.opencop.core.api.general.business.model.Caller;
import com.opencop.core.api.general.ports.usecase.connection.setup.ReplaceConnectionSetup;
import com.opencop.core.sdk.business.model.integration.connection.input.ConnectionInputResource;
import com.opencop.core.sdk.model.connection.ConnectionInfo;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Encoding;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartHttpServletRequest;

/**
 * Replaces how a connection connects. The body is a creation request without its {@code name}, so a
 * client fills the same form to change a connection as to create one.
 */
@RestController
public class ReplaceConnectionSetupEndpoint {

  private static final String SETUP_PATH = "/v2/connections/{sourceId}/setup";
  private static final String DESCRIPTION =
      "Replaces the connection's whole setup, settings included, and restarts it on the new one;"
          + " its name, visibility and permissions are kept. The body is a creation request"
          + " without `name`, which is rejected with UNKNOWN_REQUEST_FIELD; secrets and files are"
          + " always sent again. A Kafka connection is rejected with"
          + " CONNECTION_SETUP_NOT_SUPPORTED, a different `kind` with"
          + " CONNECTION_KIND_CHANGE_UNSUPPORTED, and an invalid setup with the creation codes.";

  private final CreateConnectionRequestReader reader;
  private final ReplaceConnectionSetup replaceSetup;

  ReplaceConnectionSetupEndpoint(
      CreateConnectionRequestReader reader, ReplaceConnectionSetup replaceSetup) {
    this.reader = reader;
    this.replaceSetup = replaceSetup;
  }

  @Operation(summary = "Replace a connection's setup", description = DESCRIPTION)
  @PutMapping(value = SETUP_PATH, consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<ConnectionInfo> replace(
      @AuthenticationPrincipal OpenCopPrincipal principal,
      @PathVariable UUID sourceId,
      // Documented on the parameter: springdoc drops an @Operation request body when it merges
      // this operation with the multipart one sharing its path.
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
              required = true,
              content =
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON_VALUE,
                      schema = @Schema(implementation = ConnectionSetupRequest.class)))
          @RequestBody
          String body) {
    return replace(principal, sourceId, body, List.of());
  }

  @Operation(
      summary = "Replace a connection's setup with uploaded files",
      description =
          "The `body` part holds the same JSON as an application/json request. Each file field"
              + " arrives as a part named after the field. "
              + DESCRIPTION,
      requestBody =
          @io.swagger.v3.oas.annotations.parameters.RequestBody(
              required = true,
              content =
                  @Content(
                      mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                      encoding =
                          @Encoding(
                              name = CreateConnectionMultipart.BODY_PART,
                              contentType = MediaType.APPLICATION_JSON_VALUE))))
  @PutMapping(value = SETUP_PATH, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ConnectionInfo> replaceWithFiles(
      @AuthenticationPrincipal OpenCopPrincipal principal,
      @PathVariable UUID sourceId,
      MultipartHttpServletRequest request) {
    return replace(
        principal,
        sourceId,
        CreateConnectionMultipart.body(request),
        CreateConnectionMultipart.files(request));
  }

  private ResponseEntity<ConnectionInfo> replace(
      OpenCopPrincipal principal, UUID sourceId, String body, List<ConnectionInputResource> files) {
    // Built once and passed through: the caller the setup records is the caller it is authorized
    // against.
    var caller = Caller.of(principal);
    var setup = CreateConnectionRequestMapper.toCreation(reader.readSetup(body), files, caller);
    return ResponseEntity.ok(replaceSetup.replace(sourceId, setup, caller));
  }
}
