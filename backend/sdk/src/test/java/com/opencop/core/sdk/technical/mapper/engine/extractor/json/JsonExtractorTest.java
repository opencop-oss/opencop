// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.mapper.engine.extractor.json;

import static org.junit.jupiter.api.Assertions.*;

import com.opencop.core.sdk.model.mapper.OnMissing;
import com.opencop.core.sdk.model.mapper.profile.DynamicMapper;
import com.opencop.core.sdk.model.mapper.rules.MappingRule;
import com.opencop.core.sdk.model.mapper.selector.RecordSelector;
import com.opencop.core.sdk.model.mapper.source.JsonPathSource;
import com.opencop.core.sdk.technical.mapper.engine.MapperEngineException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.core.exc.StreamConstraintsException;
import tools.jackson.databind.JsonNode;

class JsonExtractorTest {

  private static final int CONFIGURED_MAX_NESTING_DEPTH = 200;
  private static final int CONFIGURED_MAX_STRING_LENGTH = 1_000_000;

  private static final String JSON =
      """
        {
          "Tracks": [
            {
              "Lat": 39.9526,
              "Lon": -75.1652,
              "Foo": "bar"
            },
            {
              "Lat": 46.0,
              "Lon": -70.0,
              "Extra": { "Nested": 123 }
            }
          ]
        }
        """;

  @Test
  void select_records_returns_all_track_nodes_from_array() throws MapperEngineException {
    DynamicMapper profile = profileWithRecordPointer("/Tracks");
    JsonExtractor extractor = JsonExtractor.fromProfile(profile);

    List<JsonNode> records = extractor.selectRecords(bytes(JSON), profile);

    assertEquals(2, records.size());
    assertTrue(records.getFirst().isObject());
    assertTrue(records.get(1).isObject());
    assertEquals("bar", records.getFirst().get("Foo").asString());
    assertEquals(123, records.get(1).get("Extra").get("Nested").asInt());
  }

  @Test
  void select_records_returns_single_record_when_pointer_targets_object()
      throws MapperEngineException {
    DynamicMapper profile = profileWithRecordPointer("/Tracks/0");
    JsonExtractor extractor = JsonExtractor.fromProfile(profile);

    List<JsonNode> records = extractor.selectRecords(bytes(JSON), profile);

    assertEquals(1, records.size());
    assertEquals("bar", records.getFirst().get("Foo").asString());
  }

  @Test
  void select_string_extracts_scalar_value_from_record_context() throws MapperEngineException {
    DynamicMapper profile = profileWithRecordPointer("/Tracks");
    JsonExtractor extractor = JsonExtractor.fromProfile(profile);

    JsonNode first = extractor.selectRecords(bytes(JSON), profile).getFirst();

    MappingRule latRule = ruleWithPointer("/Lat");
    String lat = extractor.selectString(first, latRule);

    assertEquals("39.9526", lat);
  }

  @Test
  void select_string_extracts_nested_value() throws MapperEngineException {
    DynamicMapper profile = profileWithRecordPointer("/Tracks");
    JsonExtractor extractor = JsonExtractor.fromProfile(profile);

    JsonNode second = extractor.selectRecords(bytes(JSON), profile).get(1);

    MappingRule nestedRule = ruleWithPointer("/Extra/Nested");
    String nested = extractor.selectString(second, nestedRule);

    assertEquals("123", nested);
  }

  @Test
  void select_string_missing_value_returns_null() throws MapperEngineException {
    DynamicMapper profile = profileWithRecordPointer("/Tracks");
    JsonExtractor extractor = JsonExtractor.fromProfile(profile);

    JsonNode first = extractor.selectRecords(bytes(JSON), profile).getFirst();

    MappingRule missingRule = ruleWithPointer("/DoesNotExist");
    String out = extractor.selectString(first, missingRule);

    assertNull(out);
  }

  @Test
  void select_records_invalid_record_pointer_throws_compile_error() {
    // invalid JSON Pointer: must start with "/" (or be empty)
    DynamicMapper profile = profileWithRecordPointer("Tracks");
    JsonExtractor extractor = JsonExtractor.fromProfile(profile);

    MapperEngineException ex =
        assertThrows(
            MapperEngineException.class, () -> extractor.selectRecords(bytes(JSON), profile));

    assertEquals("JSON_POINTER_COMPILE_ERROR", ex.getCode());
  }

  @Test
  void select_string_invalid_field_pointer_throws_compile_error() throws MapperEngineException {
    DynamicMapper profile = profileWithRecordPointer("/Tracks/0");
    JsonExtractor extractor = JsonExtractor.fromProfile(profile);

    JsonNode record = extractor.selectRecords(bytes(JSON), profile).getFirst();

    MappingRule badRule = ruleWithPointer("Lat"); // invalid pointer

    MapperEngineException ex =
        assertThrows(MapperEngineException.class, () -> extractor.selectString(record, badRule));

    assertEquals("JSON_POINTER_COMPILE_ERROR", ex.getCode());
  }

  @Test
  void select_records_invalid_json_throws_parsing_error() {
    DynamicMapper profile = profileWithRecordPointer("/Tracks");
    JsonExtractor extractor = JsonExtractor.fromProfile(profile);

    String badJson = "{ nope";

    MapperEngineException ex =
        assertThrows(
            MapperEngineException.class, () -> extractor.selectRecords(bytes(badJson), profile));

    assertEquals("JSON_PARSING_ERROR", ex.getCode());
  }

  @Test
  void to_bytes_serializes_record_node() throws MapperEngineException {
    DynamicMapper profile = profileWithRecordPointer("/Tracks/0");
    JsonExtractor extractor = JsonExtractor.fromProfile(profile);

    JsonNode record = extractor.selectRecords(bytes(JSON), profile).getFirst();

    byte[] out = extractor.toBytes(record);

    assertNotNull(out);
    String text = new String(out, StandardCharsets.UTF_8);
    assertTrue(text.contains("\"Foo\""));
    assertTrue(text.contains("\"bar\""));
  }

  @Test
  void select_records_at_configured_nesting_depth_succeeds() throws MapperEngineException {
    String json = nestedArray(CONFIGURED_MAX_NESTING_DEPTH);

    List<JsonNode> records = selectRoot(json);

    assertEquals(1, records.size());
  }

  @Test
  void select_records_exceeding_nesting_depth_throws_stream_constraints_error() {
    String json = nestedArray(CONFIGURED_MAX_NESTING_DEPTH + 1);

    assertStreamConstraintsExceeded(json);
  }

  @Test
  void select_records_at_configured_string_length_succeeds() throws MapperEngineException {
    String json = jsonString(CONFIGURED_MAX_STRING_LENGTH);

    List<JsonNode> records = selectRoot(json);

    assertEquals(CONFIGURED_MAX_STRING_LENGTH, records.getFirst().asString().length());
  }

  @Test
  void select_records_exceeding_string_length_throws_stream_constraints_error() {
    String json = jsonString(CONFIGURED_MAX_STRING_LENGTH + 1);

    assertStreamConstraintsExceeded(json);
  }

  private static List<JsonNode> selectRoot(String json) throws MapperEngineException {
    DynamicMapper profile = profileWithRecordPointer("");
    return JsonExtractor.fromProfile(profile).selectRecords(bytes(json), profile);
  }

  private static void assertStreamConstraintsExceeded(String json) {
    DynamicMapper profile = profileWithRecordPointer("");
    JsonExtractor extractor = JsonExtractor.fromProfile(profile);

    MapperEngineException ex =
        assertThrows(
            MapperEngineException.class, () -> extractor.selectRecords(bytes(json), profile));

    assertEquals("JSON_PARSING_ERROR", ex.getCode());
    assertInstanceOf(StreamConstraintsException.class, ex.getCause());
  }

  private static String nestedArray(int depth) {
    return "[".repeat(depth) + "]".repeat(depth);
  }

  private static String jsonString(int length) {
    return "\"" + "a".repeat(length) + "\"";
  }

  private static byte[] bytes(String s) {
    return s.getBytes(StandardCharsets.UTF_8);
  }

  private static DynamicMapper profileWithRecordPointer(String recordPointer) {
    return new DynamicMapper(
        UUID.randomUUID(),
        true,
        null,
        new RecordSelector(new JsonPathSource(recordPointer), null),
        List.of());
  }

  private static MappingRule ruleWithPointer(String pointerExpr) {
    return new MappingRule(
        UUID.randomUUID(),
        true,
        null,
        null,
        new JsonPathSource(pointerExpr),
        null,
        OnMissing.NULL,
        null);
  }
}
