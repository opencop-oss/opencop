// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.connection.create;

import com.opencop.core.api.general.business.model.Caller;
import com.opencop.core.api.general.ports.usecase.connection.create.CreateConnection;
import com.opencop.core.sdk.business.model.integration.connection.input.ConnectionInputResource;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Encoding;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartHttpServletRequest;

/** Creates a connection from a v2 request. */
@RestController
public class CreateConnectionEndpoint {

  private static final String INTEGRATION_REJECTIONS =
      "A selected integration schema version is rejected, in this order, with"
          + " INTEGRATION_NOT_FOUND (404) when the catalog does not offer it,"
          + " INTEGRATION_INACTIVE (409) when its integration is deactivated, and"
          + " INTEGRATION_UNAVAILABLE (409) when this runtime registers no implementation for it.";

  private final CreateConnectionRequestReader reader;
  private final CreateConnection createConnection;

  CreateConnectionEndpoint(
      CreateConnectionRequestReader reader, CreateConnection createConnection) {
    this.reader = reader;
    this.createConnection = createConnection;
  }

  @Operation(summary = "Create a connection", description = INTEGRATION_REJECTIONS)
  @PostMapping(value = "/v2/connections", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<ConnectionCreatedResponse> create(
      @AuthenticationPrincipal OpenCopPrincipal principal,
      // Documented on the parameter: springdoc drops an @Operation request body when it merges
      // this operation with the multipart one sharing its path.
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
              required = true,
              content =
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON_VALUE,
                      schema = @Schema(implementation = CreateConnectionRequest.class)))
          @RequestBody
          String body) {
    return create(principal, body, List.of());
  }

  @Operation(
      summary = "Create a connection that uploads files",
      description =
          "The `body` part holds the same JSON as an application/json request. Each file field"
              + " arrives as a part named after the field; repeating a name supplies a list. "
              + INTEGRATION_REJECTIONS,
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
  @PostMapping(value = "/v2/connections", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ConnectionCreatedResponse> createWithFiles(
      @AuthenticationPrincipal OpenCopPrincipal principal, MultipartHttpServletRequest request) {
    return create(
        principal,
        CreateConnectionMultipart.body(request),
        CreateConnectionMultipart.files(request));
  }

  private ResponseEntity<ConnectionCreatedResponse> create(
      OpenCopPrincipal principal, String body, List<ConnectionInputResource> files) {
    var creation =
        CreateConnectionRequestMapper.toCreation(reader.read(body), files, Caller.of(principal));
    var result = createConnection.create(creation);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ConnectionCreatedResponseMapper.toResponse(result));
  }
}
