// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.mapper.engine.session;

import static org.junit.jupiter.api.Assertions.*;

import com.opencop.core.sdk.model.mapper.MappingError;
import com.opencop.core.sdk.model.mapper.MappingResponse;
import com.opencop.core.sdk.model.mapper.OnMissing;
import com.opencop.core.sdk.model.mapper.config.JsonFormatConfig;
import com.opencop.core.sdk.model.mapper.profile.DynamicMapper;
import com.opencop.core.sdk.model.mapper.rules.MappedTargetField;
import com.opencop.core.sdk.model.mapper.rules.MappingRule;
import com.opencop.core.sdk.model.mapper.rules.TargetField;
import com.opencop.core.sdk.model.mapper.source.ConstSource;
import com.opencop.core.sdk.model.mapper.transform.TransformStepBuilder;
import com.opencop.core.sdk.technical.mapper.MappingProperties;
import com.opencop.core.sdk.technical.mapper.engine.MapperEngineException;
import com.opencop.core.sdk.technical.mapper.engine.extractor.RecordExtractor;
import com.opencop.core.sdk.technical.mapper.engine.transformer.TransformEngine;
import com.opencop.core.sdk.technical.mapper.engine.transformer.TransformRegistryEngine;
import com.opencop.core.sdk.technical.mapper.engine.transformer.operation.GeohashToPointHandler;
import com.opencop.core.sdk.technical.model.opencop.parser.OpenCopPropertiesParser;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.geojson.Point;
import org.junit.jupiter.api.Test;

class MappingSessionTest {

  @Test
  void execute_maps_record_into_feature() {
    byte[] payload = "payload".getBytes(StandardCharsets.UTF_8);
    TestRecord record =
        new TestRecord(
            payload,
            Map.of(
                "point", "POINT",
                "displayName", "Alpha",
                "objectId", "event-1",
                "pointColor", "#112233",
                "updatedAt", "2024-01-01T00:00:00Z",
                "startAt", "2024-01-01T00:00:00Z",
                "staleAt", "2024-01-02T00:00:00Z"));
    MappingSession<TestRecord> session =
        new MappingSession<>(
            transformEngine(),
            profile(
                rule(TargetField.GEOMETRY_POINT, "point", OnMissing.NULL, null),
                rule(TargetField.DISPLAY_NAME, "displayName", OnMissing.NULL, null),
                rule(TargetField.OBJECT_ID, "objectId", OnMissing.NULL, null),
                rule(TargetField.ELEMENT_POINT_COLOR, "pointColor", OnMissing.NULL, null),
                rule(TargetField.UPDATED_AT, "updatedAt", OnMissing.NULL, null),
                rule(TargetField.START_AT, "startAt", OnMissing.NULL, null),
                rule(TargetField.STALE_AT, "staleAt", OnMissing.NULL, null)),
            new TestExtractor(List.of(record)),
            properties(false));

    MappingResponse response = session.execute(payload);

    assertFalse(response.hasErrors());
    assertEquals(1, response.records().size());
    assertTrue(response.warnings().isEmpty());

    var mappedRecord = response.records().getFirst();
    assertEquals(0, mappedRecord.index());
    assertEquals(1, mappedRecord.output().size());
    assertTrue(mappedRecord.warnings().isEmpty());
    assertInstanceOf(Point.class, mappedRecord.output().getFirst().getGeometry());
  }

  @Test
  void execute_returns_record_error_and_skips_record_when_required_value_missing() {
    byte[] payload = "record-payload".getBytes(StandardCharsets.UTF_8);
    TestRecord record = new TestRecord(payload, Map.of("point", "POINT", "displayName", ""));
    MappingSession<TestRecord> session =
        new MappingSession<>(
            transformEngine(),
            profile(
                rule(TargetField.GEOMETRY_POINT, "point", OnMissing.NULL, null),
                rule(TargetField.DISPLAY_NAME, "displayName", OnMissing.ERROR, null)),
            new TestExtractor(List.of(record)),
            properties(false));

    MappingResponse response = session.execute(payload);

    assertTrue(response.records().isEmpty());
    assertEquals(1, response.errors().size());

    MappingError error = response.errors().getFirst();
    assertEquals("ON_MISSING_ERROR", error.code());
    assertEquals("Encountered an empty value on a non optional field", error.message());
    assertArrayEquals(payload, error.payload());
    assertEquals(0, error.recordIndex());
  }

  @Test
  void execute_treats_whitespace_only_transform_input_as_missing_before_coercion() {
    byte[] payload = "record-payload".getBytes(StandardCharsets.UTF_8);
    TestRecord record = new TestRecord(payload, Map.of("coord", "   "));
    MappingSession<TestRecord> session =
        new MappingSession<>(
            new TransformRegistryEngine(List.of(new GeohashToPointHandler())),
            profile(
                rule(
                    TargetField.GEOMETRY_POINT,
                    "coord",
                    List.of(TransformStepBuilder.geohashToPoint()),
                    OnMissing.ERROR,
                    null)),
            new TestExtractor(List.of(record)),
            properties(false));

    MappingResponse response = session.execute(payload);

    assertTrue(response.records().isEmpty());
    assertEquals(1, response.errors().size());
    assertEquals("ON_MISSING_ERROR", response.errors().getFirst().code());
  }

  @Test
  void execute_omits_payload_in_preview_mode_when_record_selection_fails() {
    byte[] payload = "preview-payload".getBytes(StandardCharsets.UTF_8);
    RecordExtractor<TestRecord> extractor =
        new RecordExtractor<>() {
          @Override
          public byte[] toBytes(TestRecord recordEntry) {
            return recordEntry.payload();
          }

          @Override
          public List<TestRecord> selectRecords(byte[] ignored, DynamicMapper profile)
              throws MapperEngineException {
            throw new MapperEngineException("SELECT_ERROR", "Could not select records", null);
          }

          @Override
          public String selectString(TestRecord recordEntry, MappingRule rule) {
            return null;
          }
        };

    MappingSession<TestRecord> session =
        new MappingSession<>(transformEngine(), profile(), extractor, properties(true));

    MappingResponse response = session.execute(payload);

    assertTrue(response.records().isEmpty());
    assertEquals(1, response.errors().size());

    MappingError error = response.errors().getFirst();
    assertEquals("SELECT_ERROR", error.code());
    assertEquals("Could not select records", error.message());
    assertNull(error.payload());
    assertEquals(-1, error.recordIndex());
  }

  @Test
  void execute_uses_const_source_value_without_calling_extractor_select_string() {
    byte[] payload = "payload".getBytes(StandardCharsets.UTF_8);
    TestRecord record = new TestRecord(payload, Map.of());
    RecordExtractor<TestRecord> extractor =
        new RecordExtractor<>() {
          @Override
          public byte[] toBytes(TestRecord recordEntry) {
            return recordEntry.payload();
          }

          @Override
          public List<TestRecord> selectRecords(byte[] ignored, DynamicMapper profile) {
            return List.of(record);
          }

          @Override
          public String selectString(TestRecord recordEntry, MappingRule rule) {
            throw new AssertionError("selectString should not be called for const sources");
          }
        };

    MappingSession<TestRecord> session =
        new MappingSession<>(
            transformEngine(),
            profile(
                constRule(TargetField.GEOMETRY_POINT, "POINT", OnMissing.NULL, null),
                constRule(TargetField.DISPLAY_NAME, "Alpha", OnMissing.NULL, null)),
            extractor,
            properties(false));

    MappingResponse response = session.execute(payload);

    assertFalse(response.hasErrors());
    assertEquals(1, response.records().size());

    var feature = response.records().getFirst().output().getFirst();
    var properties = OpenCopPropertiesParser.readFromFeature(feature);
    assertEquals("Alpha", properties.getDisplayName());
    assertInstanceOf(Point.class, feature.getGeometry());
  }

  private static TransformEngine transformEngine() {
    return (input, steps) -> {
      if ("POINT".equals(input)) {
        return new Point(-75.1652, 39.9526);
      }
      return input;
    };
  }

  private static DynamicMapper profile(MappingRule... rules) {
    return new DynamicMapper(
        UUID.randomUUID(), true, new JsonFormatConfig(false), null, List.of(rules));
  }

  private static MappingRule rule(
      TargetField field, String contextRef, OnMissing onMissing, String defaultValue) {
    return rule(field, contextRef, List.of(), onMissing, defaultValue);
  }

  private static MappingRule rule(
      TargetField field,
      String contextRef,
      List<com.opencop.core.sdk.model.mapper.transform.TransformStep> transforms,
      OnMissing onMissing,
      String defaultValue) {
    return new MappingRule(
        UUID.randomUUID(),
        true,
        new MappedTargetField(field),
        contextRef,
        null,
        transforms,
        onMissing,
        defaultValue);
  }

  private static MappingRule constRule(
      TargetField field, String value, OnMissing onMissing, String defaultValue) {
    return new MappingRule(
        UUID.randomUUID(),
        true,
        new MappedTargetField(field),
        null,
        new ConstSource(value),
        List.of(),
        onMissing,
        defaultValue);
  }

  private static MappingProperties properties(boolean preview) {
    return new MappingProperties(UUID.randomUUID(), UUID.randomUUID(), preview);
  }

  private record TestRecord(byte[] payload, Map<String, String> values) {}

  private static final class TestExtractor implements RecordExtractor<TestRecord> {

    private final List<TestRecord> records;

    private TestExtractor(List<TestRecord> records) {
      this.records = records;
    }

    @Override
    public byte[] toBytes(TestRecord recordEntry) {
      return recordEntry.payload();
    }

    @Override
    public List<TestRecord> selectRecords(byte[] payload, DynamicMapper profile) {
      return records;
    }

    @Override
    public String selectString(TestRecord recordEntry, MappingRule rule) {
      return recordEntry.values().get(rule.contextRef());
    }
  }
}
