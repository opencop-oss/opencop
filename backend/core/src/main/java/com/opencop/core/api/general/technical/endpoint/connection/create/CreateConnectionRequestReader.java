// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.connection.create;

import com.opencop.core.api.general.ports.usecase.connection.ConnectionExceptionCodes;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionManagementException;
import com.opencop.core.api.general.technical.endpoint.connection.StrictRequestBodies;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.exc.InvalidTypeIdException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

/**
 * Reads a v2 creation request strictly, as {@link StrictRequestBodies} describes: a field the
 * request's kind does not define, such as one belonging to another kind or a retired {@code
 * profileId}, is rejected rather than ignored.
 */
@Component
public class CreateConnectionRequestReader {

  private static final String KIND_FIELD = "kind";
  private static final String NAME_FIELD = "name";
  // The property naming a nested choice, such as framing's CUSTOM or INTEGRATION.
  private static final String NESTED_TYPE_PROPERTY = "type";

  private final ObjectMapper strictMapper;

  CreateConnectionRequestReader(ObjectMapper applicationMapper) {
    this.strictMapper = StrictRequestBodies.strict(applicationMapper);
  }

  /**
   * Reads a setup that replaces a connection's: a creation request without the {@code name}, which
   * a setup leaves as it is and so does not define.
   */
  CreateConnectionRequest readSetup(String body) {
    JsonNode tree;
    try {
      tree = strictMapper.readTree(body);
    } catch (JacksonException _) {
      throw StrictRequestBodies.invalidBody();
    }
    if (tree != null && tree.isObject() && tree.has(NAME_FIELD)) {
      throw ConnectionManagementException.forField(
          ConnectionExceptionCodes.UNKNOWN_REQUEST_FIELD, NAME_FIELD, NAME_FIELD);
    }
    return read(body);
  }

  CreateConnectionRequest read(String body) {
    CreateConnectionRequest request;
    try {
      request = strictMapper.readValue(body, CreateConnectionRequest.class);
    } catch (UnrecognizedPropertyException exception) {
      throw StrictRequestBodies.unknownField(exception);
    } catch (InvalidTypeIdException exception) {
      throw unsupportedType(exception);
    } catch (JacksonException _) {
      throw StrictRequestBodies.invalidBody();
    }
    // A JSON null is well-formed but describes no connection.
    if (request == null) {
      throw StrictRequestBodies.invalidBody();
    }
    return request;
  }

  /**
   * A missing or unknown discriminator. At the top level it is the connection's kind; inside a
   * field, such as {@code framing}, it is that field's own type, which the client is pointed at
   * instead of a kind it sent correctly.
   */
  private static ConnectionManagementException unsupportedType(InvalidTypeIdException exception) {
    var path = StrictRequestBodies.propertyPath(exception);
    if (path.isEmpty()) {
      return ConnectionManagementException.forField(
          ConnectionExceptionCodes.UNSUPPORTED_CONNECTION_KIND, KIND_FIELD);
    }
    var field = path + "." + NESTED_TYPE_PROPERTY;
    return ConnectionManagementException.forField(
        ConnectionExceptionCodes.REQUIRED_FIELD_INVALID, field, field);
  }
}
