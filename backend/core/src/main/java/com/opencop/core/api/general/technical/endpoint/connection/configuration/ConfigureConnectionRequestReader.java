// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.connection.configuration;

import com.opencop.core.api.general.business.model.connection.configuration.ConnectionConfigurationChange;
import com.opencop.core.api.general.technical.endpoint.connection.StrictRequestBodies;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

/**
 * Reads a live configuration change strictly, as {@link StrictRequestBodies} describes, so a field
 * it does not define, such as {@code integration}, is rejected rather than silently ignored.
 */
@Component
public class ConfigureConnectionRequestReader {

  private static final TypeReference<Map<String, Object>> SETTINGS_DOCUMENT =
      new TypeReference<>() {};

  private final ObjectMapper strictMapper;

  ConfigureConnectionRequestReader(ObjectMapper applicationMapper) {
    this.strictMapper = StrictRequestBodies.strict(applicationMapper);
  }

  ConnectionConfigurationChange read(String body) {
    ConfigureConnectionRequest request;
    try {
      request = strictMapper.readValue(body, ConfigureConnectionRequest.class);
    } catch (UnrecognizedPropertyException exception) {
      throw StrictRequestBodies.unknownField(exception);
    } catch (JacksonException _) {
      throw StrictRequestBodies.invalidBody();
    }
    if (request == null) {
      throw StrictRequestBodies.invalidBody();
    }
    return new ConnectionConfigurationChange(
        request.access(), request.mapperId(), request.egressMapperId(), settings(request));
  }

  /** Absent changes nothing, {@code null} clears, and an object replaces the whole document. */
  private Optional<Map<String, ?>> settings(ConfigureConnectionRequest request) {
    JsonNode settings = request.settings();
    if (settings == null || settings.isMissingNode()) {
      return Optional.empty();
    }
    if (settings.isNull()) {
      return Optional.of(Map.of());
    }
    if (!settings.isObject()) {
      throw StrictRequestBodies.invalidBody();
    }
    return Optional.of(strictMapper.treeToValue(settings, SETTINGS_DOCUMENT));
  }
}
