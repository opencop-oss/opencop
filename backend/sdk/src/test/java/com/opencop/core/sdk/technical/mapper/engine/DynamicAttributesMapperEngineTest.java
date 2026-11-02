// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.mapper.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opencop.core.sdk.model.mapper.MappingRequest;
import com.opencop.core.sdk.model.mapper.MappingResponse;
import com.opencop.core.sdk.model.mapper.OnMissing;
import com.opencop.core.sdk.model.mapper.config.CsvFormatConfig;
import com.opencop.core.sdk.model.mapper.config.JsonFormatConfig;
import com.opencop.core.sdk.model.mapper.config.XmlFormatConfig;
import com.opencop.core.sdk.model.mapper.profile.DynamicMapper;
import com.opencop.core.sdk.model.mapper.rules.MappedTargetField;
import com.opencop.core.sdk.model.mapper.rules.MappingRule;
import com.opencop.core.sdk.model.mapper.rules.TargetField;
import com.opencop.core.sdk.model.mapper.selector.RecordSelector;
import com.opencop.core.sdk.model.mapper.source.CsvColumnSource;
import com.opencop.core.sdk.model.mapper.source.JsonPathSource;
import com.opencop.core.sdk.model.mapper.source.SourceRef;
import com.opencop.core.sdk.model.mapper.source.XPathSource;
import com.opencop.core.sdk.technical.mapper.MapperFacade;
import com.opencop.core.sdk.technical.mapper.MappingProperties;
import com.opencop.core.sdk.technical.model.opencop.parser.OpenCopPropertiesParser;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(MappingEngineTestConfig.class)
class DynamicAttributesMapperEngineTest {

  @Autowired MapperFacade facade;

  @Test
  void maps_json_scalar_fields_and_legacy_flat_map_to_attributes() {
    String json =
        """
        {
          "tracks": [
            {
              "lat": 39.9526,
              "lon": -75.1652,
              "name": "Alpha",
              "status": "active",
              "battery": 87,
              "attrs": {
                "team": "blue",
                "priority": 2
              }
            }
          ]
        }
        """;

    DynamicMapper profile =
        new DynamicMapper(
            UUID.randomUUID(),
            true,
            new JsonFormatConfig(false),
            new RecordSelector(new JsonPathSource("/tracks"), OnMissing.ERROR),
            List.of(
                jsonRule(TargetField.GEOMETRY_LAT, "/lat"),
                jsonRule(TargetField.GEOMETRY_LON, "/lon"),
                jsonRule(TargetField.DISPLAY_NAME, "/name"),
                jsonRule(TargetField.ATTRIBUTES, "/attrs"),
                jsonAttributeRule("/status", "status"),
                jsonAttributeRule("/battery", "battery")));

    MappingResponse response = map(json, profile);

    assertFalse(response.hasErrors());
    Map<String, String> attributes = attributes(response);
    assertEquals("blue", attributes.get("team"));
    assertEquals("2", attributes.get("priority"));
    assertEquals("active", attributes.get("status"));
    assertEquals("87", attributes.get("battery"));
  }

  @Test
  void legacy_json_flat_map_drops_nested_attribute_values_and_keeps_primitives() {
    String json =
        """
        {
          "tracks": [
            {
              "lat": 39.9526,
              "lon": -75.1652,
              "name": "Alpha",
              "attrs": {
                "team": "blue",
                "battery": 87,
                "active": true,
                "meta": {
                  "foo": "bar"
                },
                "tags": ["blue", "moving"]
              }
            }
          ]
        }
        """;

    DynamicMapper profile =
        new DynamicMapper(
            UUID.randomUUID(),
            true,
            new JsonFormatConfig(false),
            new RecordSelector(new JsonPathSource("/tracks"), OnMissing.ERROR),
            List.of(
                jsonRule(TargetField.GEOMETRY_LAT, "/lat"),
                jsonRule(TargetField.GEOMETRY_LON, "/lon"),
                jsonRule(TargetField.DISPLAY_NAME, "/name"),
                jsonRule(TargetField.ATTRIBUTES, "/attrs")));

    MappingResponse response = map(json, profile);

    assertFalse(response.hasErrors());
    Map<String, String> attributes = attributes(response);
    assertEquals("blue", attributes.get("team"));
    assertEquals("87", attributes.get("battery"));
    assertEquals("true", attributes.get("active"));
    assertFalse(attributes.containsKey("meta"));
    assertFalse(attributes.containsKey("tags"));
    assertTrue(hasWarning(response, "ATTRIBUTE_NESTED_VALUE_DROPPED"));
  }

  @Test
  void multiple_legacy_json_flat_maps_are_merged() {
    String json =
        """
        {
          "tracks": [
            {
              "lat": 39.9526,
              "lon": -75.1652,
              "name": "Alpha",
              "attrsA": {
                "team": "blue",
                "battery": 87
              },
              "attrsB": {
                "status": "active",
                "priority": 2
              }
            }
          ]
        }
        """;

    DynamicMapper profile =
        new DynamicMapper(
            UUID.randomUUID(),
            true,
            new JsonFormatConfig(false),
            new RecordSelector(new JsonPathSource("/tracks"), OnMissing.ERROR),
            List.of(
                jsonRule(TargetField.GEOMETRY_LAT, "/lat"),
                jsonRule(TargetField.GEOMETRY_LON, "/lon"),
                jsonRule(TargetField.DISPLAY_NAME, "/name"),
                jsonRule(TargetField.ATTRIBUTES, "/attrsA"),
                jsonRule(TargetField.ATTRIBUTES, "/attrsB")));

    MappingResponse response = map(json, profile);

    assertFalse(response.hasErrors());
    assertEquals(
        Map.of("team", "blue", "battery", "87", "status", "active", "priority", "2"),
        attributes(response));
  }

  @Test
  void json_scalar_attribute_mode_drops_selected_nested_values_and_keeps_primitives() {
    String json =
        """
        {
          "tracks": [
            {
              "lat": 39.9526,
              "lon": -75.1652,
              "name": "Alpha",
              "device": {
                "status": "active",
                "name": {
                  "foo": "bar"
                },
                "tags": ["blue", "moving"]
              }
            }
          ]
        }
        """;

    DynamicMapper profile =
        new DynamicMapper(
            UUID.randomUUID(),
            true,
            new JsonFormatConfig(false),
            new RecordSelector(new JsonPathSource("/tracks"), OnMissing.ERROR),
            List.of(
                jsonRule(TargetField.GEOMETRY_LAT, "/lat"),
                jsonRule(TargetField.GEOMETRY_LON, "/lon"),
                jsonRule(TargetField.DISPLAY_NAME, "/name"),
                jsonAttributeRule("/device/status", "status"),
                jsonAttributeRule("/device/name", "deviceName"),
                jsonAttributeRule("/device/tags", "tags")));

    MappingResponse response = map(json, profile);

    assertFalse(response.hasErrors());
    Map<String, String> attributes = attributes(response);
    assertEquals(Map.of("status", "active"), attributes);
    assertTrue(hasWarning(response, "ATTRIBUTE_NESTED_VALUE_DROPPED"));
  }

  @Test
  void maps_xml_scalar_fields_to_attributes() {
    String xml =
        """
        <Tracks>
          <Track>
            <Lat>39.9526</Lat>
            <Lon>-75.1652</Lon>
            <Name>Bravo</Name>
            <Status>idle</Status>
            <Battery>44</Battery>
          </Track>
        </Tracks>
        """;

    DynamicMapper profile =
        new DynamicMapper(
            UUID.randomUUID(),
            true,
            new XmlFormatConfig(Map.of(), true),
            new RecordSelector(new XPathSource("//Track"), OnMissing.ERROR),
            List.of(
                xmlRule(TargetField.GEOMETRY_LAT, "Lat/text()"),
                xmlRule(TargetField.GEOMETRY_LON, "Lon/text()"),
                xmlRule(TargetField.DISPLAY_NAME, "Name/text()"),
                xmlAttributeRule("Status/text()", "status"),
                xmlAttributeRule("Battery/text()", "battery")));

    MappingResponse response = map(xml, profile);

    assertFalse(response.hasErrors());
    assertEquals(Map.of("status", "idle", "battery", "44"), attributes(response));
  }

  @Test
  void maps_csv_scalar_fields_to_attributes() {
    String csv =
        """
        lat,lon,name,status,battery
        39.9526,-75.1652,Charlie,moving,91
        """;

    DynamicMapper profile =
        new DynamicMapper(
            UUID.randomUUID(),
            true,
            CsvFormatConfig.defaults(),
            null,
            List.of(
                csvRule(TargetField.GEOMETRY_LAT, "lat"),
                csvRule(TargetField.GEOMETRY_LON, "lon"),
                csvRule(TargetField.DISPLAY_NAME, "name"),
                csvAttributeRule("status", "status"),
                csvAttributeRule("battery", "battery")));

    MappingResponse response = map(csv, profile);

    assertFalse(response.hasErrors());
    assertEquals(Map.of("status", "moving", "battery", "91"), attributes(response));
  }

  @Test
  void duplicate_attribute_key_warns_and_uses_later_rule_value() {
    String json =
        """
        {
          "tracks": [
            {
              "lat": 39.9526,
              "lon": -75.1652,
              "name": "Delta",
              "status": "old",
              "overrideStatus": "new"
            }
          ]
        }
        """;

    DynamicMapper profile =
        new DynamicMapper(
            UUID.randomUUID(),
            true,
            new JsonFormatConfig(false),
            new RecordSelector(new JsonPathSource("/tracks"), OnMissing.ERROR),
            List.of(
                jsonRule(TargetField.GEOMETRY_LAT, "/lat"),
                jsonRule(TargetField.GEOMETRY_LON, "/lon"),
                jsonRule(TargetField.DISPLAY_NAME, "/name"),
                jsonAttributeRule("/status", "status"),
                jsonAttributeRule("/overrideStatus", "status")));

    MappingResponse response = map(json, profile);

    assertFalse(response.hasErrors());
    assertEquals("new", attributes(response).get("status"));
    assertTrue(hasWarning(response, "ATTRIBUTE_DUPLICATE_KEY"));
  }

  private MappingResponse map(String payload, DynamicMapper profile) {
    return facade.map(
        new MappingRequest(
            new MappingProperties(UUID.randomUUID(), UUID.randomUUID(), false),
            profile,
            payload.getBytes(StandardCharsets.UTF_8)));
  }

  private static Map<String, String> attributes(MappingResponse response) {
    return OpenCopPropertiesParser.readFromFeature(
            response.records().getFirst().output().getFirst())
        .getAttributes();
  }

  private static boolean hasWarning(MappingResponse response, String code) {
    return response.records().getFirst().warnings().stream()
        .anyMatch(warning -> code.equals(warning.code()));
  }

  private static MappingRule jsonRule(TargetField target, String pointer) {
    return rule(target, new JsonPathSource(pointer), null);
  }

  private static MappingRule jsonAttributeRule(String pointer, String attributeKey) {
    return rule(TargetField.ATTRIBUTES, new JsonPathSource(pointer), attributeKey);
  }

  private static MappingRule xmlRule(TargetField target, String xpath) {
    return rule(target, new XPathSource(xpath), null);
  }

  private static MappingRule xmlAttributeRule(String xpath, String attributeKey) {
    return rule(TargetField.ATTRIBUTES, new XPathSource(xpath), attributeKey);
  }

  private static MappingRule csvRule(TargetField target, String column) {
    return rule(target, new CsvColumnSource(column, null), null);
  }

  private static MappingRule csvAttributeRule(String column, String attributeKey) {
    return rule(TargetField.ATTRIBUTES, new CsvColumnSource(column, null), attributeKey);
  }

  private static MappingRule rule(TargetField target, SourceRef source, String attributeKey) {
    return new MappingRule(
        UUID.randomUUID(),
        true,
        new MappedTargetField(target),
        null,
        source,
        List.of(),
        OnMissing.ERROR,
        null,
        attributeKey);
  }
}
