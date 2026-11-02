// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.mapper.engine;

import static org.junit.jupiter.api.Assertions.*;

import com.opencop.core.sdk.model.mapper.MappingWarning;
import com.opencop.core.sdk.model.mapper.OnMissing;
import com.opencop.core.sdk.model.mapper.rules.MappedTargetField;
import com.opencop.core.sdk.model.mapper.rules.MappingRule;
import com.opencop.core.sdk.model.mapper.rules.TargetField;
import com.opencop.core.sdk.technical.gateway.extractor.KmzIconRef;
import com.opencop.core.sdk.technical.mapper.MappingProperties;
import com.opencop.core.sdk.technical.mapper.engine.session.SessionField;
import com.opencop.core.sdk.technical.model.opencop.OpenCopProperties;
import com.opencop.core.sdk.technical.model.opencop.elements.MediaIconElement;
import com.opencop.core.sdk.technical.model.opencop.elements.PointElement;
import com.opencop.core.sdk.technical.model.opencop.parser.OpenCopPropertiesParser;
import java.time.Instant;
import java.util.*;
import org.geojson.Feature;
import org.geojson.GeometryCollection;
import org.geojson.LineString;
import org.geojson.LngLatAlt;
import org.geojson.Point;
import org.geojson.Polygon;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FeatureBuilderTest {

  private static final Point SAMPLE_POINT = new Point(-75.1652, 39.9526);

  @Nested
  class MediaIcons {

    @Test
    void media_prefixed_href_passes_through_as_is() throws MapperEngineException {
      OpenCopProperties props = mapIcon("opencop:media:abc-123");

      assertEquals("media-icon", props.getElement().getType());
      assertEquals("opencop:media:abc-123", props.getElement().get(MediaIconElement.HREF));
      assertNull(props.getElement().get(PointElement.SYMBOL));
    }

    @Test
    void http_url_still_passes_through() throws MapperEngineException {
      OpenCopProperties props = mapIcon("https://example.com/icon.png");

      assertEquals("media-icon", props.getElement().getType());
      assertEquals("https://example.com/icon.png", props.getElement().get(MediaIconElement.HREF));
    }

    @Test
    void data_uri_passes_through() throws MapperEngineException {
      OpenCopProperties props = mapIcon("data:image/png;base64,iVBORw0KGgo=");

      assertEquals("media-icon", props.getElement().getType());
      assertEquals(
          "data:image/png;base64,iVBORw0KGgo=", props.getElement().get(MediaIconElement.HREF));
    }

    @Test
    void local_path_without_manifest_passes_through_raw() throws MapperEngineException {
      OpenCopProperties props = mapIcon("icons/marker.png");

      assertEquals("media-icon", props.getElement().getType());
      assertEquals("icons/marker.png", props.getElement().get(MediaIconElement.HREF));
    }

    @Test
    void local_path_with_manifest_resolves_to_media() throws MapperEngineException {
      UUID mediaId = UUID.fromString("11111111-1111-1111-1111-111111111111");
      var manifest =
          List.of(
              new KmzIconRef(
                  "doc.kml",
                  "icons/marker.png",
                  "icons/marker.png",
                  mediaId,
                  KmzIconRef.Status.RESOLVED,
                  64,
                  64));

      Map<String, SessionField> fields = iconFields("icons/marker.png");
      MappingProperties mappingProps =
          new MappingProperties(UUID.randomUUID(), UUID.randomUUID(), false, manifest);
      Feature feature = buildSingle(fields, mappingProps);
      OpenCopProperties props = OpenCopPropertiesParser.readFromFeature(feature);

      assertEquals("media-icon", props.getElement().getType());
      assertEquals("opencop:media:" + mediaId, props.getElement().get(MediaIconElement.HREF));
    }

    @Test
    void a_sidc_rule_wins_over_an_icon_rule() throws MapperEngineException {
      Map<String, SessionField> fields = iconFields("https://example.com/icon.png");
      fields.put(
          TargetField.ELEMENT_SYMBOL_SIDC.path(),
          sessionField(TargetField.ELEMENT_SYMBOL_SIDC, "10031000000000000000"));

      OpenCopProperties props = OpenCopPropertiesParser.readFromFeature(buildSingle(fields));

      assertEquals("symbol", props.getElement().getType());
      assertEquals("10031000000000000000", props.getElement().get(PointElement.SYMBOL));
      assertNull(props.getElement().get(MediaIconElement.HREF));
    }

    @Test
    void a_missing_icon_location_fails_with_its_own_code() {
      Map<String, SessionField> fields = iconFields(null);

      MapperEngineException ex =
          assertThrows(MapperEngineException.class, () -> buildSingle(fields));

      assertEquals("ICON_LOCATION_ERROR", ex.getCode());
    }

    @Test
    void the_built_element_reads_back_as_a_media_icon() throws MapperEngineException {
      OpenCopProperties props = mapIcon("https://example.com/icon.png");

      var element = new MediaIconElement();
      props.getElement().getEntries().forEach(e -> element.set(e.getKey(), e.getValue()));

      assertEquals("https://example.com/icon.png", element.getHref());
    }

    private static OpenCopProperties mapIcon(String location) throws MapperEngineException {
      return OpenCopPropertiesParser.readFromFeature(buildSingle(iconFields(location)));
    }

    private static Map<String, SessionField> iconFields(String location) {
      Map<String, SessionField> fields = new HashMap<>();
      fields.put(
          TargetField.GEOMETRY_POINT.path(),
          sessionField(TargetField.GEOMETRY_POINT, SAMPLE_POINT));
      fields.put(
          TargetField.ELEMENT_ICON_LOCATION.path(),
          sessionField(TargetField.ELEMENT_ICON_LOCATION, location));
      return fields;
    }
  }

  @Nested
  class MissingIconDegradation {

    @Test
    void no_symbol_icon_falls_through_to_plain_point() throws MapperEngineException {
      Map<String, SessionField> fields = new HashMap<>();
      fields.put(
          TargetField.GEOMETRY_POINT.path(),
          sessionField(TargetField.GEOMETRY_POINT, SAMPLE_POINT));

      Feature feature = buildSingle(fields);
      OpenCopProperties props = OpenCopPropertiesParser.readFromFeature(feature);

      assertEquals("point", props.getElement().getType());
      assertNotNull(props.getElement().get(PointElement.FILL_COLOR));
    }
  }

  @Nested
  class MotionFields {

    @Test
    void point_elements_include_motion_fields() throws MapperEngineException {
      Map<String, SessionField> fields = withMotion(baseFields());

      BuildResult result = buildResult(fields);
      OpenCopProperties props = OpenCopPropertiesParser.readFromFeature(result.feature());

      assertEquals("point", props.getElement().getType());
      assertEquals(123.4d, ((Number) props.getElement().get("course")).doubleValue());
      assertEquals(12.5d, ((Number) props.getElement().get("speed")).doubleValue());
      assertEquals(91.25d, ((Number) props.getElement().get("heading")).doubleValue());
    }

    @Test
    void line_elements_include_motion_fields() throws MapperEngineException {
      Map<String, SessionField> fields = withMotion(baseFields());
      LineString geometry = new LineString();
      geometry.add(new LngLatAlt(-75.1652, 39.9526));
      geometry.add(new LngLatAlt(-75.15, 39.97));
      fields.put(TargetField.GEOMETRY.path(), sessionField(TargetField.GEOMETRY, geometry));
      fields.remove(TargetField.GEOMETRY_POINT.path());

      BuildResult result = buildResult(fields);
      OpenCopProperties props = OpenCopPropertiesParser.readFromFeature(result.feature());

      assertEquals("line", props.getElement().getType());
      assertEquals(123.4d, ((Number) props.getElement().get("course")).doubleValue());
      assertEquals(12.5d, ((Number) props.getElement().get("speed")).doubleValue());
      assertEquals(91.25d, ((Number) props.getElement().get("heading")).doubleValue());
    }

    @Test
    void polygon_elements_include_motion_fields() throws MapperEngineException {
      Map<String, SessionField> fields = withMotion(baseFields());
      fields.put(
          TargetField.GEOMETRY.path(),
          sessionField(
              TargetField.GEOMETRY,
              new Polygon(
                  List.of(
                      new LngLatAlt(-75.1652, 39.9526),
                      new LngLatAlt(-75.15, 39.9526),
                      new LngLatAlt(-75.15, 39.97),
                      new LngLatAlt(-75.1652, 39.9526)))));
      fields.remove(TargetField.GEOMETRY_POINT.path());

      BuildResult result = buildResult(fields);
      OpenCopProperties props = OpenCopPropertiesParser.readFromFeature(result.feature());

      assertEquals("polygon", props.getElement().getType());
      assertEquals(123.4d, ((Number) props.getElement().get("course")).doubleValue());
      assertEquals(12.5d, ((Number) props.getElement().get("speed")).doubleValue());
      assertEquals(91.25d, ((Number) props.getElement().get("heading")).doubleValue());
    }
  }

  @Nested
  class GeometryCollections {

    @Test
    void empty_geometry_collection_with_motion_fields_throws_mapper_exception() {
      Map<String, SessionField> fields = withMotion(baseFields());
      GeometryCollection geometry = new GeometryCollection();
      fields.put(TargetField.GEOMETRY.path(), sessionField(TargetField.GEOMETRY, geometry));
      fields.remove(TargetField.GEOMETRY_POINT.path());

      MapperEngineException ex =
          assertThrows(MapperEngineException.class, () -> buildResult(fields));

      assertEquals("ELEMENT_ERROR", ex.getCode());
      assertTrue(ex.getMessage().contains("empty list of geometry"));
    }
  }

  @Nested
  class StyleFields {

    @Test
    void point_color_is_normalized_when_hex_has_no_hash_or_alpha() throws MapperEngineException {
      Map<String, SessionField> fields = baseFields();
      fields.put(
          TargetField.ELEMENT_POINT_COLOR.path(),
          sessionField(TargetField.ELEMENT_POINT_COLOR, "112233"));

      BuildResult result = buildResult(fields);
      OpenCopProperties props = OpenCopPropertiesParser.readFromFeature(result.feature());

      assertEquals("point", props.getElement().getType());
      assertEquals("#112233b3", props.getElement().get(PointElement.FILL_COLOR));
    }

    @Test
    void line_style_fields_are_mapped_and_shorthand_color_is_expanded()
        throws MapperEngineException {
      Map<String, SessionField> fields = baseFields();
      LineString geometry = new LineString();
      geometry.add(new LngLatAlt(-75.1652, 39.9526));
      geometry.add(new LngLatAlt(-75.15, 39.97));
      fields.put(TargetField.GEOMETRY.path(), sessionField(TargetField.GEOMETRY, geometry));
      fields.remove(TargetField.GEOMETRY_POINT.path());
      fields.put(
          TargetField.STYLE_STROKE_STYLE.path(),
          sessionField(TargetField.STYLE_STROKE_STYLE, "dashed"));
      fields.put(
          TargetField.STYLE_STROKE_WIDTH.path(), sessionField(TargetField.STYLE_STROKE_WIDTH, 5d));
      fields.put(
          TargetField.STYLE_STROKE_COLOR.path(),
          sessionField(TargetField.STYLE_STROKE_COLOR, "abc"));

      BuildResult result = buildResult(fields);
      OpenCopProperties props = OpenCopPropertiesParser.readFromFeature(result.feature());

      assertEquals("line", props.getElement().getType());
      assertEquals("dashed", props.getElement().get("strokeStyle"));
      assertEquals(5d, ((Number) props.getElement().get("strokeWeight")).doubleValue());
      assertEquals("#aabbccb3", props.getElement().get("strokeColor"));
    }

    @Test
    void polygon_missing_style_fields_default_and_emit_force_default_warnings()
        throws MapperEngineException {
      Map<String, SessionField> fields = baseFields();
      fields.put(
          TargetField.GEOMETRY.path(),
          sessionField(
              TargetField.GEOMETRY,
              new Polygon(
                  List.of(
                      new LngLatAlt(-75.1652, 39.9526),
                      new LngLatAlt(-75.15, 39.9526),
                      new LngLatAlt(-75.15, 39.97),
                      new LngLatAlt(-75.1652, 39.9526)))));
      fields.remove(TargetField.GEOMETRY_POINT.path());

      BuildResult result = buildResult(fields);
      OpenCopProperties props = OpenCopPropertiesParser.readFromFeature(result.feature());

      assertEquals("polygon", props.getElement().getType());
      assertEquals("#80808033", props.getElement().get("fillColor"));
      assertEquals("solid", props.getElement().get("strokeStyle"));
      assertEquals(2d, ((Number) props.getElement().get("strokeWeight")).doubleValue());
      assertEquals("#808080b3", props.getElement().get("strokeColor"));
      assertEquals(
          4,
          result.warnings().stream().filter(w -> "FORCE_FIELD_DEFAULT".equals(w.code())).count());
    }

    @Test
    void invalid_color_format_throws_mapper_exception() {
      Map<String, SessionField> fields = baseFields();
      fields.put(
          TargetField.STYLE_FILL_COLOR.path(), sessionField(TargetField.STYLE_FILL_COLOR, "12345"));
      fields.put(
          TargetField.GEOMETRY.path(),
          sessionField(
              TargetField.GEOMETRY,
              new Polygon(
                  List.of(
                      new LngLatAlt(-75.1652, 39.9526),
                      new LngLatAlt(-75.15, 39.9526),
                      new LngLatAlt(-75.15, 39.97),
                      new LngLatAlt(-75.1652, 39.9526)))));
      fields.remove(TargetField.GEOMETRY_POINT.path());

      MapperEngineException ex =
          assertThrows(MapperEngineException.class, () -> buildResult(fields));

      assertEquals("COLOR_FORMAT_ERROR", ex.getCode());
      assertTrue(ex.getMessage().contains("Invalid hex color format"));
    }

    @Test
    void wrong_style_field_type_throws_mapper_exception() {
      Map<String, SessionField> fields = baseFields();
      fields.put(
          TargetField.STYLE_STROKE_WIDTH.path(),
          sessionField(TargetField.STYLE_STROKE_WIDTH, "wide"));
      LineString geometry = new LineString();
      geometry.add(new LngLatAlt(-75.1652, 39.9526));
      geometry.add(new LngLatAlt(-75.15, 39.97));
      fields.put(TargetField.GEOMETRY.path(), sessionField(TargetField.GEOMETRY, geometry));
      fields.remove(TargetField.GEOMETRY_POINT.path());

      MapperEngineException ex =
          assertThrows(MapperEngineException.class, () -> buildResult(fields));

      assertEquals("FEATURE_BUILDER_ERROR", ex.getCode());
      assertTrue(ex.getMessage().contains("STYLE_STROKE_WIDTH"));
      assertTrue(ex.getMessage().contains("Double"));
    }
  }

  @Nested
  class Attributes {

    @Test
    void attributes_are_normalized_to_string_map() throws MapperEngineException {
      Map<String, SessionField> fields = baseFields();
      Map<Object, Object> raw = new LinkedHashMap<>();
      raw.put("string", "value");
      raw.put("number", 42);
      raw.put("bool", true);
      raw.put("nullValue", null);
      raw.put(99, "ignored");
      fields.put(TargetField.ATTRIBUTES.path(), sessionField(TargetField.ATTRIBUTES, raw));

      BuildResult result = buildResult(fields);
      OpenCopProperties props = OpenCopPropertiesParser.readFromFeature(result.feature());

      Map<String, String> expected = new LinkedHashMap<>();
      expected.put("string", "value");
      expected.put("number", "42");
      expected.put("bool", "true");
      expected.put("nullValue", null);

      assertEquals(expected, props.getAttributes());
      assertFalse(props.getAttributes().containsKey("99"));
    }

    @Test
    void nested_attribute_values_are_dropped_with_warnings() throws MapperEngineException {
      Map<String, SessionField> fields = baseFields();
      Map<Object, Object> raw = new LinkedHashMap<>();
      raw.put("string", "value");
      raw.put("nestedObject", Map.of("foo", "bar"));
      raw.put("nestedArray", List.of("blue", "moving"));
      fields.put(TargetField.ATTRIBUTES.path(), sessionField(TargetField.ATTRIBUTES, raw));

      BuildResult result = buildResult(fields);
      OpenCopProperties props = OpenCopPropertiesParser.readFromFeature(result.feature());

      assertEquals(Map.of("string", "value"), props.getAttributes());
      assertEquals(
          2,
          result.warnings().stream()
              .filter(w -> "ATTRIBUTE_NESTED_VALUE_DROPPED".equals(w.code()))
              .count());
    }

    @Test
    void empty_attributes_map_resolves_to_null() throws MapperEngineException {
      Map<String, SessionField> fields = baseFields();
      Map<Object, Object> raw = new LinkedHashMap<>();
      raw.put(99, "ignored");
      fields.put(TargetField.ATTRIBUTES.path(), sessionField(TargetField.ATTRIBUTES, raw));

      BuildResult result = buildResult(fields);
      OpenCopProperties props = OpenCopPropertiesParser.readFromFeature(result.feature());

      assertNull(props.getAttributes());
    }
  }

  private static Feature buildSingle(Map<String, SessionField> fields)
      throws MapperEngineException {
    return buildResult(fields).feature();
  }

  private static Feature buildSingle(Map<String, SessionField> fields, MappingProperties props)
      throws MapperEngineException {
    return buildResult(fields, props).feature();
  }

  private static BuildResult buildResult(Map<String, SessionField> fields)
      throws MapperEngineException {
    return buildResult(fields, new MappingProperties(UUID.randomUUID(), UUID.randomUUID(), false));
  }

  private static BuildResult buildResult(Map<String, SessionField> fields, MappingProperties props)
      throws MapperEngineException {
    List<MappingWarning> warnings = new ArrayList<>();
    FeatureBuilder builder = new FeatureBuilder(fields, props, warnings, 0);
    List<Feature> features = builder.build();
    assertEquals(1, features.size());
    return new BuildResult(features.getFirst(), warnings);
  }

  private static Map<String, SessionField> baseFields() {
    Map<String, SessionField> fields = new HashMap<>();
    fields.put(
        TargetField.GEOMETRY_POINT.path(), sessionField(TargetField.GEOMETRY_POINT, SAMPLE_POINT));
    fields.put(
        TargetField.DISPLAY_NAME.path(), sessionField(TargetField.DISPLAY_NAME, "feature-name"));
    fields.put(
        TargetField.UPDATED_AT.path(),
        sessionField(TargetField.UPDATED_AT, Instant.parse("2026-01-01T00:00:00Z")));
    fields.put(
        TargetField.START_AT.path(),
        sessionField(TargetField.START_AT, Instant.parse("2026-01-01T00:00:00Z")));
    fields.put(
        TargetField.STALE_AT.path(),
        sessionField(TargetField.STALE_AT, Instant.parse("2026-01-02T00:00:00Z")));
    return fields;
  }

  private static Map<String, SessionField> withMotion(Map<String, SessionField> fields) {
    fields.put(TargetField.ELEMENT_COURSE.path(), sessionField(TargetField.ELEMENT_COURSE, 123.4d));
    fields.put(TargetField.ELEMENT_SPEED.path(), sessionField(TargetField.ELEMENT_SPEED, 12.5d));
    fields.put(
        TargetField.ELEMENT_HEADING.path(), sessionField(TargetField.ELEMENT_HEADING, 91.25d));
    return fields;
  }

  private static SessionField sessionField(TargetField target, Object value) {
    MappingRule rule =
        new MappingRule(
            UUID.randomUUID(),
            true,
            new MappedTargetField(target),
            null,
            null,
            List.of(),
            OnMissing.NULL,
            null);
    return new SessionField(rule, value);
  }

  private record BuildResult(Feature feature, List<MappingWarning> warnings) {}
}
