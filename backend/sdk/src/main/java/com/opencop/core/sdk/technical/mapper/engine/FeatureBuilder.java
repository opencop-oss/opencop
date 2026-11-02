// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.mapper.engine;

import static com.opencop.core.sdk.model.mapper.rules.TargetField.ATTRIBUTES;
import static com.opencop.core.sdk.model.mapper.rules.TargetField.DISPLAY_NAME;
import static com.opencop.core.sdk.model.mapper.rules.TargetField.ELEMENT_COURSE;
import static com.opencop.core.sdk.model.mapper.rules.TargetField.ELEMENT_HEADING;
import static com.opencop.core.sdk.model.mapper.rules.TargetField.ELEMENT_ICON_LOCATION;
import static com.opencop.core.sdk.model.mapper.rules.TargetField.ELEMENT_POINT_COLOR;
import static com.opencop.core.sdk.model.mapper.rules.TargetField.ELEMENT_SPEED;
import static com.opencop.core.sdk.model.mapper.rules.TargetField.ELEMENT_SYMBOL_SIDC;
import static com.opencop.core.sdk.model.mapper.rules.TargetField.GEOMETRY;
import static com.opencop.core.sdk.model.mapper.rules.TargetField.GEOMETRY_LAT;
import static com.opencop.core.sdk.model.mapper.rules.TargetField.GEOMETRY_LON;
import static com.opencop.core.sdk.model.mapper.rules.TargetField.GEOMETRY_POINT;
import static com.opencop.core.sdk.model.mapper.rules.TargetField.OBJECT_ID;
import static com.opencop.core.sdk.model.mapper.rules.TargetField.STALE_AT;
import static com.opencop.core.sdk.model.mapper.rules.TargetField.START_AT;
import static com.opencop.core.sdk.model.mapper.rules.TargetField.STYLE_FILL_COLOR;
import static com.opencop.core.sdk.model.mapper.rules.TargetField.STYLE_STROKE_COLOR;
import static com.opencop.core.sdk.model.mapper.rules.TargetField.STYLE_STROKE_STYLE;
import static com.opencop.core.sdk.model.mapper.rules.TargetField.STYLE_STROKE_WIDTH;
import static com.opencop.core.sdk.model.mapper.rules.TargetField.UPDATED_AT;

import com.opencop.core.sdk.model.mapper.MappingWarning;
import com.opencop.core.sdk.model.mapper.rules.TargetField;
import com.opencop.core.sdk.technical.gateway.extractor.KmzIconRef;
import com.opencop.core.sdk.technical.mapper.MappingProperties;
import com.opencop.core.sdk.technical.mapper.engine.session.SessionField;
import com.opencop.core.sdk.technical.model.opencop.OpenCopProperties;
import com.opencop.core.sdk.technical.model.opencop.Timestampz;
import com.opencop.core.sdk.technical.model.opencop.elements.Element;
import com.opencop.core.sdk.technical.model.opencop.elements.ElementTypes;
import com.opencop.core.sdk.technical.model.opencop.elements.LineElement;
import com.opencop.core.sdk.technical.model.opencop.elements.MediaIconElement;
import com.opencop.core.sdk.technical.model.opencop.elements.PointElement;
import com.opencop.core.sdk.technical.model.opencop.elements.ShapeElement;
import com.opencop.core.sdk.technical.model.opencop.parser.OpenCopPropertiesParser;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.geojson.Feature;
import org.geojson.GeoJsonObject;
import org.geojson.GeometryCollection;
import org.geojson.LineString;
import org.geojson.MultiLineString;
import org.geojson.MultiPoint;
import org.geojson.MultiPolygon;
import org.geojson.Point;
import org.geojson.Polygon;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class FeatureBuilder {

  public static final int DEFAULT_STALE_DAYS_OFFSET = 365;

  private static final ObjectMapper mapper = new ObjectMapper();

  private final int index;
  private final List<MappingWarning> warnings;
  private final Map<String, SessionField> fields;
  private final MappingProperties properties;

  public FeatureBuilder(
      Map<String, SessionField> fields,
      MappingProperties properties,
      List<MappingWarning> warnings,
      int index) {
    this.warnings = warnings;
    this.index = index;
    this.fields = fields;
    this.properties = properties;
  }

  public List<Feature> build() throws MapperEngineException {
    GeoJsonObject geo = resolveGeometry();
    return List.of(resolveGeoJson(properties, geo));
  }

  private Feature resolveGeoJson(MappingProperties mappingProperties, GeoJsonObject geo)
      throws MapperEngineException {
    OpenCopProperties properties = new OpenCopProperties();

    properties.setElement(resolveElement(geo));
    this.hydrateMotion(properties.getElement());

    var objectId =
        fields.get(OBJECT_ID.path()) != null
            ? fields.get(OBJECT_ID.path()).value().toString()
            : UUID.randomUUID().toString();
    properties.setObjectId(objectId);

    SessionField displayNameField = fields.get(DISPLAY_NAME.path());
    if (displayNameField != null) {
      if (displayNameField.value() instanceof String displayName) {
        properties.setDisplayName(displayName);
      } else {
        warnings.add(
            new MappingWarning(
                "DISPLAY_NAME_ERROR",
                "The display name is empty, generating a random UUID as display name",
                List.of(displayNameField.rule().id()),
                index));
        properties.setDisplayName(UUID.randomUUID().toString());
      }
    } else {
      warnings.add(
          new MappingWarning(
              "DISPLAY_NAME_ERROR",
              "A display name was not provided, generating a random UUID as display name",
              null,
              -1));
      properties.setDisplayName(UUID.randomUUID().toString());
    }

    properties.setTimestampz(resolveTimestamp());

    this.verifyTimestamps(properties);

    properties.setId(UUID.randomUUID());
    properties.setSource(mappingProperties.sourceId());
    properties.setImportId(mappingProperties.importId());

    properties.setAttributes(resolveAttributes());

    return OpenCopPropertiesParser.writeToFeature(properties, geo);
  }

  private void verifyTimestamps(OpenCopProperties properties) {
    var start = Instant.ofEpochMilli(properties.getTimestampz().getStart());
    var time = Instant.ofEpochMilli(properties.getTimestampz().getTime());
    var stale = Instant.ofEpochMilli(properties.getTimestampz().getStale());
    if (Duration.between(start, stale).isNegative() || Duration.between(start, stale).isZero()) {
      warnings.add(
          new MappingWarning(
              "DATE_TIME", "The stale time is before or equal to the start time", null, index));
    }
    if (Duration.between(time, stale).isNegative() || Duration.between(time, stale).isZero()) {
      warnings.add(
          new MappingWarning(
              "DATE_TIME", "The stale time is before or equal to the event time", null, index));
    }
  }

  private GeoJsonObject resolveGeometry() throws MapperEngineException {
    if (fields.containsKey(GEOMETRY.path())) {
      SessionField field = fields.get(GEOMETRY.path());
      if (field.value() instanceof GeoJsonObject geometry) {
        return geometry;
      } else if (field.value() instanceof String s && !s.isBlank()) {
        try {
          return mapper.readValue(s, GeoJsonObject.class);
        } catch (Exception e) {
          throw new MapperEngineException(
              "FEATURE_BUILDER_ERROR",
              "Invalid GeoJSON geometry string",
              field.rule().id(),
              e,
              index);
        }
      }
      throw new MapperEngineException(
          "FEATURE_BUILDER_ERROR", "Invalid GeoJSON geometry", null, index);
    }

    if (fields.containsKey(GEOMETRY_LAT.path()) && fields.containsKey(GEOMETRY_LON.path())) {
      var lonField = fields.get(GEOMETRY_LON.path());
      var latField = fields.get(GEOMETRY_LAT.path());
      if (latField == null || lonField == null) {
        throw new MapperEngineException(
            "FEATURE_BUILDER_ERROR", "The Lon or Lat is null", null, index);
      }
      if (latField.value() instanceof Double lat && lonField.value() instanceof Double lon) {
        return new Point(lon, lat);
      }
      throw new MapperEngineException(
          "FEATURE_BUILDER_ERROR", "The Lon or Lat is null", null, index);
    }

    if (!fields.containsKey(GEOMETRY_POINT.path())) {
      throw new MapperEngineException(
          "FEATURE_BUILDER_ERROR", "The geometry point provided is not a Point", null, index);
    }
    if (fields.get(GEOMETRY_POINT.path()).value() instanceof Point p) {
      return p;
    }
    throw new MapperEngineException(
        "FEATURE_BUILDER_ERROR",
        "Could not find a geometry",
        fields.get(GEOMETRY_POINT.path()).rule().id(),
        null,
        index);
  }

  private Element resolveElement(GeoJsonObject geometry) throws MapperEngineException {
    if (geometry instanceof GeometryCollection geometries) {
      if (geometries.getGeometries() == null || geometries.getGeometries().isEmpty()) {
        throw new MapperEngineException(
            "ELEMENT_ERROR", "Found a multi geometry with an empty list of geometry", null, index);
      }
      Element element = null;
      for (GeoJsonObject geom : geometries.getGeometries()) {
        var el = this.resolveElement(geom);
        if (element == null) {
          element = el;
        } else {
          element.getFields().putAll(el.getFields());
        }
      }
      return element;
    }
    if (geometry instanceof LineString || geometry instanceof MultiLineString) {
      return resolveLineElement();
    } else if (geometry instanceof Point || geometry instanceof MultiPoint) {
      return resolvePointElement();
    } else if (geometry instanceof Polygon || geometry instanceof MultiPolygon) {
      return resolvePolygonElement();
    }
    throw new MapperEngineException(
        "ELEMENT_ERROR", "Could not find an event element to map to", null, index);
  }

  private Element resolvePointElement() throws MapperEngineException {
    if (fields.containsKey(ELEMENT_SYMBOL_SIDC.path())) {
      SessionField field = fields.get(ELEMENT_SYMBOL_SIDC.path());
      if (field == null || !(field.value() instanceof String symbolIcon)) {
        throw new MapperEngineException(
            "ICON_ELEMENT_ERROR",
            "Tried to map the event as a symbol but the symbol was null",
            null,
            index);
      }
      return new PointElement.Builder()
          .symbol(symbolIcon.replace("-", ""))
          .type(ElementTypes.SYMBOL)
          .build();
    }
    if (fields.containsKey(ELEMENT_ICON_LOCATION.path())) {
      SessionField field = fields.get(ELEMENT_ICON_LOCATION.path());
      if (field == null || !(field.value() instanceof String iconLocation)) {
        throw new MapperEngineException(
            "ICON_LOCATION_ERROR",
            "Tried to map the event as a media icon but the icon location was null",
            null,
            index);
      }
      return new MediaIconElement.Builder().href(resolveIconHref(iconLocation)).build();
    }
    String color =
        normalizeHexColor(
            getPropertyOrDefault(ELEMENT_POINT_COLOR, String.class, "#808080b3"), "b3");
    return new PointElement.Builder().fillColor(color).type(ElementTypes.POINT).build();
  }

  private void hydrateMotion(Element element) {
    if (fields.containsKey(ELEMENT_COURSE.path())
        && fields.get(ELEMENT_COURSE.path()).value() instanceof Double course) {
      element.setCourse(course);
    }
    if (fields.containsKey(ELEMENT_SPEED.path())
        && fields.get(ELEMENT_SPEED.path()).value() instanceof Double speed) {
      element.setSpeed(speed);
    }
    if (fields.containsKey(ELEMENT_HEADING.path())
        && fields.get(ELEMENT_HEADING.path()).value() instanceof Double heading) {
      element.setHeading(heading);
    }
  }

  private Element resolveLineElement() throws MapperEngineException {
    return new LineElement.Builder()
        .strokeStyle(getPropertyOrDefault(STYLE_STROKE_STYLE, String.class, "solid"))
        .strokeWeight(getPropertyOrDefault(STYLE_STROKE_WIDTH, Double.class, 2d))
        .strokeColor(
            normalizeHexColor(
                getPropertyOrDefault(STYLE_STROKE_COLOR, String.class, "#808080b3"), "b3"))
        .type(ElementTypes.LINE)
        .build();
  }

  private Element resolvePolygonElement() throws MapperEngineException {

    return new ShapeElement.Builder()
        .fillColor(
            normalizeHexColor(
                getPropertyOrDefault(STYLE_FILL_COLOR, String.class, "#80808033"), "33"))
        .strokeStyle(getPropertyOrDefault(STYLE_STROKE_STYLE, String.class, "solid"))
        .strokeWeight(getPropertyOrDefault(STYLE_STROKE_WIDTH, Double.class, 2d))
        .strokeColor(
            normalizeHexColor(
                getPropertyOrDefault(STYLE_STROKE_COLOR, String.class, "#808080b3"), "b3"))
        .type(ElementTypes.POLYGON)
        .build();
  }

  /**
   * Resolves a raw icon href to a media icon's {@link MediaIconElement#HREF}. Looks up the manifest
   * (if present) to convert local archive-relative hrefs to opencop:media:{mediaId} URIs.
   */
  private String resolveIconHref(String rawHref) {
    // Already-resolved hrefs and external URLs pass through as-is. This also makes processing
    // idempotent — if a message is replayed, opencop:media:{id} values won't be re-looked-up.
    if (rawHref.startsWith("https://")
        || rawHref.startsWith("http://")
        || rawHref.startsWith("opencop:")) {
      return rawHref;
    }

    // Manifest lookup: convert archive-relative hrefs to opencop:media:{mediaId}
    if (!properties.iconManifest().isEmpty()) {
      KmzIconRef ref = KmzIconRef.lookupByHref(properties.iconManifest(), rawHref);
      if (ref != null) {
        String symbolValue = ref.toSymbolValue();
        if (symbolValue != null) {
          return symbolValue;
        }
      }
    }

    // Fallback: no manifest or unresolved — pass raw href as-is (degrades to missing icon)
    return rawHref;
  }

  private <T> T getPropertyOrDefault(TargetField field, Class<T> valueType, T defaultValue)
      throws MapperEngineException {
    SessionField sessionField = fields.get(field.path());
    if (sessionField != null && sessionField.value() != null) {
      Object value = sessionField.value();
      if (valueType.isInstance(value)) {
        return valueType.cast(value);
      }
      throw new MapperEngineException(
          "FEATURE_BUILDER_ERROR",
          "The mapping for %s is not a %s".formatted(field, valueType.getSimpleName()),
          sessionField.rule().id(),
          null,
          index);
    }
    warnings.add(
        new MappingWarning(
            "FORCE_FIELD_DEFAULT",
            "The mapping for %s was defaulted forcefully to %s".formatted(field, defaultValue),
            null,
            index));
    return defaultValue;
  }

  private String normalizeHexColor(String color, String defaultAlpha) throws MapperEngineException {
    if (color == null || color.isBlank()) {
      return null;
    }

    String normalized = color.trim();

    if (!normalized.startsWith("#")) {
      normalized = "#" + normalized;
    }

    // Remove #
    String hex = normalized.substring(1);

    // 6 chars → add alpha
    if (hex.length() == 6) {
      return "#" + hex + defaultAlpha;
    }

    // 8 chars → already contains alpha
    if (hex.length() == 8) {
      return "#" + hex;
    }

    // Optional: handle 3-char shorthand (#FFF → #FFFFFF)
    if (hex.length() == 3) {
      String expanded =
          ""
              + hex.charAt(0)
              + hex.charAt(0)
              + hex.charAt(1)
              + hex.charAt(1)
              + hex.charAt(2)
              + hex.charAt(2);
      return "#" + expanded + defaultAlpha;
    }

    throw new MapperEngineException(
        "COLOR_FORMAT_ERROR", "Invalid hex color format: " + color, null, index);
  }

  private Timestampz resolveTimestamp() {
    Timestampz timestampz = new Timestampz();

    timestampz.setTime(getTimestamp(UPDATED_AT, 0));
    timestampz.setStale(getTimestamp(STALE_AT, DEFAULT_STALE_DAYS_OFFSET));
    timestampz.setStart(getTimestamp(START_AT, 0));

    return timestampz;
  }

  private long getTimestamp(TargetField targetField, int defaultOffset) {
    var defaultValue = Instant.now().plus(Duration.ofDays(defaultOffset));
    if (!fields.containsKey(targetField.path())) {
      warnings.add(
          new MappingWarning(
              "TIMESTAMP_DEFAULT",
              "The mapping for %s was defaulted forcefully to %s"
                  .formatted(targetField, defaultValue),
              null,
              index));
    } else if (fields.get(targetField.path()).value() instanceof Instant start) {
      return start.toEpochMilli();
    } else {
      warnings.add(
          new MappingWarning(
              "TIMESTAMP_DEFAULT",
              "The mapping for %s was defaulted forcefully to %s"
                  .formatted(targetField, defaultValue),
              List.of(fields.get(targetField.path()).rule().id()),
              index));
    }
    return defaultValue.toEpochMilli();
  }

  private Map<String, String> resolveAttributes() {
    Map<String, String> result = new LinkedHashMap<>();

    for (SessionField field : fields.values()) {
      if (field == null || field.rule() == null || field.rule().target().field() != ATTRIBUTES) {
        continue;
      }

      if (field.rule().isAttributeEntry()) {
        putAttribute(result, field.rule().attributeKey(), field.value(), field);
        continue;
      }

      if (field.value() instanceof Map<?, ?> raw) {
        mergeAttributeMap(result, raw, field);
      }
    }

    return result.isEmpty() ? null : result;
  }

  private void mergeAttributeMap(
      Map<String, String> result, Map<?, ?> raw, SessionField sessionField) {
    for (Map.Entry<?, ?> entry : raw.entrySet()) {

      Object key = entry.getKey();
      if (!(key instanceof String k)) {
        // Skip invalid keys
        continue;
      }

      putAttribute(result, k, entry.getValue(), sessionField);
    }
  }

  private void putAttribute(
      Map<String, String> result, String key, Object value, SessionField sessionField) {
    if (key == null || key.isBlank()) {
      return;
    }

    String normalizedKey = key.trim();
    if (isNestedAttributeValue(value)) {
      warnings.add(
          new MappingWarning(
              "ATTRIBUTE_NESTED_VALUE_DROPPED",
              "Dropped nested attribute value for key %s".formatted(normalizedKey),
              List.of(sessionField.rule().id()),
              index));
      return;
    }

    if (result.containsKey(normalizedKey)) {
      warnings.add(
          new MappingWarning(
              "ATTRIBUTE_DUPLICATE_KEY",
              "The attribute key %s was mapped more than once; using the last value"
                  .formatted(normalizedKey),
              List.of(sessionField.rule().id()),
              index));
    }

    if (value == null) {
      result.put(normalizedKey, null);
    } else if (value instanceof String s) {
      result.put(normalizedKey, s);
    } else {
      // Normalize non-string values safely
      result.put(normalizedKey, String.valueOf(value));
    }
  }

  private boolean isNestedAttributeValue(Object value) {
    if (value == null) {
      return false;
    }
    if (value instanceof Map<?, ?>
        || value instanceof Collection<?>
        || value.getClass().isArray()) {
      return true;
    }
    if (value instanceof JsonNode node) {
      return node.isObject() || node.isArray();
    }
    if (value instanceof String s) {
      return isJsonContainerString(s);
    }
    return false;
  }

  private boolean isJsonContainerString(String value) {
    String trimmed = value.trim();
    if (trimmed.isEmpty()) {
      return false;
    }
    if (!(trimmed.startsWith("{") || trimmed.startsWith("["))) {
      return false;
    }
    try {
      JsonNode node = mapper.readTree(trimmed);
      return node != null && (node.isObject() || node.isArray());
    } catch (Exception _) {
      return false;
    }
  }
}
