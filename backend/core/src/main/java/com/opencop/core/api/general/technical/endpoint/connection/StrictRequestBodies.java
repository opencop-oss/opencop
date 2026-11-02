// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.connection;

import com.opencop.core.api.general.ports.usecase.connection.ConnectionExceptionCodes;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionManagementException;
import java.util.Objects;
import java.util.stream.Collectors;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

/**
 * Reads connection request bodies strictly: a field the request does not define is rejected rather
 * than ignored.
 *
 * <p>The application's mapper ignores unknown fields, so a field belonging to another request, a
 * retired one, or a typo would otherwise be silently dropped. A strict mapper keeps every other
 * setting of that mapper and turns only that one on.
 */
public final class StrictRequestBodies {

  private StrictRequestBodies() {}

  /** The application's mapper, rejecting fields a request does not define. */
  public static ObjectMapper strict(ObjectMapper applicationMapper) {
    return applicationMapper
        .rebuild()
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build();
  }

  /**
   * Names the unknown field by its full path, such as {@code integration.alias}, so a client can
   * find a field nested inside another.
   */
  public static ConnectionManagementException unknownField(
      UnrecognizedPropertyException exception) {
    var path = propertyPath(exception);
    var field = path.isEmpty() ? exception.getPropertyName() : path;
    return ConnectionManagementException.forField(
        ConnectionExceptionCodes.UNKNOWN_REQUEST_FIELD, field, field);
  }

  /** The dotted names of the properties leading to where reading failed. */
  public static String propertyPath(JacksonException exception) {
    return exception.getPath().stream()
        .map(JacksonException.Reference::getPropertyName)
        .filter(Objects::nonNull)
        .collect(Collectors.joining("."));
  }

  public static ConnectionManagementException invalidBody() {
    return new ConnectionManagementException(ConnectionExceptionCodes.INVALID_REQUEST_BODY);
  }
}
