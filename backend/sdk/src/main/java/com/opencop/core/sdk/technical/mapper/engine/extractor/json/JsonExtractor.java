// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.mapper.engine.extractor.json;

import com.opencop.core.sdk.model.mapper.profile.DynamicMapper;
import com.opencop.core.sdk.model.mapper.rules.MappingRule;
import com.opencop.core.sdk.model.mapper.source.JsonPathSource;
import com.opencop.core.sdk.technical.mapper.engine.MapperEngineException;
import com.opencop.core.sdk.technical.mapper.engine.extractor.RecordExtractor;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.lang3.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonPointer;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.json.JsonFactoryBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class JsonExtractor implements RecordExtractor<JsonNode> {

  private static final int MAX_NESTING_DEPTH = 200;
  private static final long MAX_DOCUMENT_LENGTH = 10_000_000L;
  private static final long MAX_TOKEN_COUNT = 2_000_000L;
  private static final int MAX_NUMBER_LENGTH = 1_000;
  private static final int MAX_STRING_LENGTH = 1_000_000;
  private static final int MAX_NAME_LENGTH = 1_000;

  private final ObjectMapper objectMapper;
  private final String recordExpr;

  // cache: pointer string -> compiled JsonPointer
  private final ConcurrentHashMap<String, JsonPointer> cache = new ConcurrentHashMap<>();

  private JsonPointer compiled(String expr) throws MapperEngineException {
    try {
      return cache.computeIfAbsent(expr, JsonPointer::compile);
    } catch (IllegalArgumentException e) {
      throw new MapperEngineException(
          "JSON_POINTER_COMPILE_ERROR",
          "An error occurred while compiling the json pointer: " + expr,
          e);
    }
  }

  private JsonExtractor(ObjectMapper objectMapper, String recordExpr) {
    this.objectMapper = objectMapper;
    this.recordExpr = recordExpr;
  }

  public static JsonExtractor fromProfile(DynamicMapper profile) {
    StreamReadConstraints constraints =
        StreamReadConstraints.builder()
            .maxNestingDepth(MAX_NESTING_DEPTH)
            .maxDocumentLength(MAX_DOCUMENT_LENGTH)
            .maxTokenCount(MAX_TOKEN_COUNT)
            .maxNumberLength(MAX_NUMBER_LENGTH)
            .maxStringLength(MAX_STRING_LENGTH)
            .maxNameLength(MAX_NAME_LENGTH)
            .build();
    ObjectMapper om =
        new ObjectMapper(new JsonFactoryBuilder().streamReadConstraints(constraints).build());
    return new JsonExtractor(om, ((JsonPathSource) profile.recordSelector().source()).expr());
  }

  private JsonNode parse(byte[] jsonBytes) throws MapperEngineException {
    try {
      return objectMapper.readTree(jsonBytes);
    } catch (Exception e) {
      throw new MapperEngineException("JSON_PARSING_ERROR", "Failed to parse JSON", e);
    }
  }

  @Override
  public byte[] toBytes(JsonNode recordEntry) {
    try {
      return objectMapper.writeValueAsBytes(recordEntry);
    } catch (JacksonException _) {
      return null;
    }
  }

  @Override
  public List<JsonNode> selectRecords(byte[] payload, DynamicMapper profile)
      throws MapperEngineException {
    try {
      JsonNode doc = parse(payload);

      JsonPointer ptr = compiled(recordExpr);
      JsonNode selected = doc.at(ptr);

      if (selected.isMissingNode() || selected.isNull()) {
        return List.of();
      }

      if (selected.isArray()) {
        List<JsonNode> out = new ArrayList<>(selected.size());
        selected.forEach(out::add);
        return out;
      }

      // single record object/value
      return List.of(selected);

    } catch (MapperEngineException e) {
      throw e;
    } catch (Exception e) {
      throw new MapperEngineException(
          "JSON_POINTER_EVAL_ERROR", "Failed to evaluate json pointer: " + recordExpr, e);
    }
  }

  @Override
  public String selectString(JsonNode context, MappingRule rule) throws MapperEngineException {
    try {
      if (!(rule.source() instanceof JsonPathSource)) {
        throw new MapperEngineException(
            "JSON_POINTER_ERROR", "Bad rule source, expected Json Source", null);
      }

      String expr = ((JsonPathSource) rule.source()).expr();

      if (StringUtils.isEmpty(expr)) {
        return null;
      }
      JsonNode node = context.at(compiled(expr));

      if (node.isMissingNode() || node.isNull()) {
        return null;
      }

      if (node.isValueNode()) {
        return node.asString();
      }

      // objects/arrays -> json string
      return node.toString();

    } catch (MapperEngineException e) {
      throw e;
    } catch (Exception e) {
      String expr = ((JsonPathSource) rule.source()).expr();
      throw new MapperEngineException("JSON_POINTER_ERROR", "Json pointer eval failed: " + expr, e);
    }
  }
}
