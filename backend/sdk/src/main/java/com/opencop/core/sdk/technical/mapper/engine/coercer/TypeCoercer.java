// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.mapper.engine.coercer;

import com.opencop.core.sdk.model.mapper.rules.ValueType;
import com.opencop.core.sdk.technical.mapper.engine.MapperEngineException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public final class TypeCoercer {

  private static final ObjectMapper JSON = new ObjectMapper();

  public Object coerce(Object input, ValueType targetType) throws MapperEngineException {
    if (input == null) {
      throw new MapperEngineException("COERCE_ERROR", "The input is null", null);
    }
    if (targetType == null) {
      throw new MapperEngineException("BAD_RULE_REGISTRATION", "The target type is null", null);
    }

    return switch (targetType) {
      case STRING -> String.valueOf(input);
      case LONG -> toLong(input);
      case DOUBLE -> toDouble(input);
      case INT -> toInt(input);
      case BOOLEAN -> toBoolean(input);
      case INSTANT -> toInstant(input);
      case MAP -> toAttributeMap(input);
      case OBJECT, ARRAY, POINT, GEOMETRY, UUID -> input;
    };
  }

  private Long toLong(Object input) throws MapperEngineException {
    if (input instanceof Long l) {
      return l;
    }
    if (input instanceof Number n) {
      return n.longValue();
    }
    String s = String.valueOf(input).trim();
    if (s.isEmpty()) {
      throw new MapperEngineException(
          "COERCE_ERROR", "Could not map value to a Long value because it was empty", null);
    }
    try {
      return Long.parseLong(s);
    } catch (NumberFormatException e) {
      throw new MapperEngineException("COERCE_ERROR", "Could not map value to a Long value", e);
    }
  }

  private Double toDouble(Object input) throws MapperEngineException {
    if (input instanceof Double d) {
      return d;
    }
    if (input instanceof Number n) {
      return n.doubleValue();
    }
    String s = String.valueOf(input).trim();
    if (s.isEmpty()) {
      throw new MapperEngineException(
          "COERCE_ERROR", "Could not map value to a Double value because it was empty", null);
    }
    try {
      return Double.parseDouble(s);
    } catch (NumberFormatException e) {
      throw new MapperEngineException("COERCE_ERROR", "Could not map value to a Double value", e);
    }
  }

  private Integer toInt(Object input) throws MapperEngineException {
    if (input instanceof Integer i) {
      return i;
    }
    if (input instanceof Number n) {
      return n.intValue();
    }

    String s = String.valueOf(input).trim();
    if (s.isEmpty()) {
      throw new MapperEngineException(
          "COERCE_ERROR", "Could not map value to an Integer value because it was empty", null);
    }
    try {
      return Integer.parseInt(s);
    } catch (NumberFormatException e) {
      throw new MapperEngineException("COERCE_ERROR", "Could not map value to an Integer value", e);
    }
  }

  private Boolean toBoolean(Object input) throws MapperEngineException {
    if (input instanceof Boolean b) {
      return b;
    }
    String s = String.valueOf(input).trim().toLowerCase();
    if (s.isEmpty()) {
      throw new MapperEngineException(
          "COERCE_ERROR", "Could not map value to a Boolean value because it was empty", null);
    }
    return switch (s) {
      case "true", "1", "yes", "y" -> true;
      case "false", "0", "no", "n" -> false;
      default ->
          throw new MapperEngineException(
              "COERCE_ERROR", "Could not map value to a Boolean, bad format", null);
    };
  }

  private Instant toInstant(Object input) throws MapperEngineException {
    if (input instanceof Instant i) {
      return i;
    }

    String s = String.valueOf(input).trim();
    if (s.isEmpty()) {
      throw new MapperEngineException(
          "COERCE_ERROR", "Could not map value to a Date because it was empty", null);
    }

    try {
      return Instant.parse(s);
    } catch (Exception _) {
      try {
        return OffsetDateTime.parse(s).toInstant();
      } catch (Exception e) {
        throw new MapperEngineException(
            "COERCE_ERROR", "Could not map value to a Date, bad format", e);
      }
    }
  }

  private Map<String, Object> toAttributeMap(Object input) throws MapperEngineException {
    if (input instanceof Map<?, ?> m) {
      Map<String, Object> out = new LinkedHashMap<>();
      for (var entry : m.entrySet()) {
        if (entry.getKey() == null) {
          continue;
        }
        String key = String.valueOf(entry.getKey()).trim();
        if (key.isEmpty()) {
          continue;
        }
        Object value = entry.getValue();
        out.put(key, value);
      }
      return out;
    }

    // Otherwise expect JSON object in a string
    String s = String.valueOf(input).trim();
    if (s.isEmpty()) {
      throw new MapperEngineException(
          "COERCE_ERROR", "Could not map value to a Map because it was empty", null);
    }

    try {
      JsonNode node = JSON.readTree(s);
      if (node == null || !node.isObject()) {
        throw new MapperEngineException(
            "COERCE_ERROR", "Could not map value to a Map, expected a JSON object", null);
      }

      Map<String, Object> out = new LinkedHashMap<>();
      for (var entry : node.properties()) {
        String k = entry.getKey();
        JsonNode v = entry.getValue();
        out.put(k, (v == null || v.isNull()) ? null : jsonAttributeValue(v));
      }
      return out;

    } catch (MapperEngineException e) {
      throw e;
    } catch (Exception e) {
      throw new MapperEngineException(
          "COERCE_ERROR", "Could not map value to a Map, invalid JSON", e);
    }
  }

  private Object jsonAttributeValue(JsonNode value) {
    if (value.isObject() || value.isArray()) {
      return value;
    }
    return value.asString();
  }
}
