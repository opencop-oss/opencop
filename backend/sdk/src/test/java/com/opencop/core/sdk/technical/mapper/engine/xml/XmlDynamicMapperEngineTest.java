// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.mapper.engine.xml;

import static org.junit.jupiter.api.Assertions.*;

import com.opencop.core.sdk.model.mapper.MappingRequest;
import com.opencop.core.sdk.model.mapper.MappingResponse;
import com.opencop.core.sdk.model.mapper.OnMissing;
import com.opencop.core.sdk.model.mapper.profile.DynamicMapper;
import com.opencop.core.sdk.model.mapper.profile.DynamicMapperBuilder;
import com.opencop.core.sdk.model.mapper.rules.MappingRule;
import com.opencop.core.sdk.model.mapper.rules.MappingRuleBuilder;
import com.opencop.core.sdk.model.mapper.rules.TargetField;
import com.opencop.core.sdk.model.mapper.transform.TransformOperation;
import com.opencop.core.sdk.model.mapper.transform.TransformStepBuilder;
import com.opencop.core.sdk.technical.mapper.MapperFacade;
import com.opencop.core.sdk.technical.mapper.MappingProperties;
import com.opencop.core.sdk.technical.mapper.engine.MappingEngineTestConfig;
import com.opencop.core.sdk.technical.model.opencop.OpenCopProperties;
import com.opencop.core.sdk.technical.model.opencop.elements.Element;
import com.opencop.core.sdk.technical.model.opencop.elements.PointElement;
import com.opencop.core.sdk.technical.model.opencop.parser.OpenCopPropertiesParser;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.geojson.Feature;
import org.geojson.Point;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(MappingEngineTestConfig.class)
class XmlDynamicMapperEngineTest {

  @Autowired MapperFacade facade;

  @Test
  void maps_lat_lon_into_geojson_point() {
    String xml =
        """
                <Track>
                  <Lat>39.9526</Lat>
                  <Lon>-75.1652</Lon>
                  <Color>#0000FF</Color>
                </Track>
                """;

    DynamicMapper profile = baseProfile();

    MappingResponse response = map(xml, profile);

    assertTrue(response.errors().isEmpty());
    assertEquals(1, response.records().size());

    List<Feature> features = response.records().getFirst().output();

    assertEquals(1, features.size());
    assertNotNull(features.getFirst().getGeometry());
    assertInstanceOf(Point.class, features.getFirst().getGeometry());

    var coords = ((Point) features.getFirst().getGeometry()).getCoordinates();
    assertEquals(-75.1652d, coords.getLongitude(), 0.00001);
    assertEquals(39.9526d, coords.getLatitude(), 0.00001);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        """
                <Track>
                  <Lon>-75.1652</Lon>
                </Track>
            """,
        """
                <Track>
                  <Lat>39.9526</Lat>
                </Track>
            """
      })
  void fails_when_required_coordinate_is_missing(String xml) {
    MappingResponse response = map(xml, baseProfile());

    assertEquals(0, response.records().size());
    assertEquals(1, response.errors().size());
    assertEquals("ON_MISSING_ERROR", response.errors().getFirst().code());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        """
                    <Track>
                      <Lat>north</Lat>
                      <Lon>-75.1652</Lon>
                      <Color>#0000FF</Color>
                    </Track>
                    """,
        """
                    <Track>
                      <Lat>39.9526</Lat>
                      <Lon>west</Lon>
                      <Color>#0000FF</Color>
                    </Track>
                    """
      })
  void fails_when_coordinate_is_not_a_number(String xml) {
    MappingResponse response = map(xml, baseProfile());

    assertEquals(0, response.records().size());
    assertEquals(1, response.errors().size());
    assertEquals("COERCE_ERROR", response.errors().getFirst().code());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        """
                    <Track>
                      <Lat> 39.9526 </Lat>
                      <Lon> -75.1652 </Lon>
                      <Color>#0000FF</Color>
                    </Track>
                    """,
        """
                    <Track>
                      <Lat>
                        39.9526
                      </Lat>
                      <Lon>
                        -75.1652
                      </Lon>
                      <Color>#0000FF</Color>
                    </Track>
                    """
      })
  void trims_whitespace_in_coordinates(String xml) {
    MappingResponse response = map(xml, baseProfile());

    assertEquals(1, response.records().size());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        """
                    <Track>
                      <Lat>39.9526</Lat>
                      <Lon>-75.1652</Lon>
                      <Foo>bar</Foo>
                      <Color>#0000FF</Color>
                    </Track>
                    """,
        """
                    <Track>
                      <Lat>39.9526</Lat>
                      <Lon>-75.1652</Lon>
                      <Extra>
                        <Nested>123</Nested>
                      </Extra>
                      <Color>#0000FF</Color>
                    </Track>
                    """
      })
  void ignores_unmapped_xml_fields(String xml) {
    MappingResponse response = map(xml, baseProfile());

    assertEquals(1, response.records().size());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        """
                    <Tracks>
                        <Track>
                          <Lat>39.9526</Lat>
                          <Lon>-75.1652</Lon>
                          <Foo>bar</Foo>
                          <Color>#0000FF</Color>
                        </Track>
                        <Track>
                          <Lat>39.9526</Lat>
                          <Lon>-75.1652</Lon>
                          <Extra>
                            <Nested>123</Nested>
                          </Extra>
                          <Color>#0000FF</Color>
                        </Track>
                    </Tracks>
                    """
      })
  void map_record_list(String xml) {
    MappingResponse response = map(xml, baseProfile());
    assertEquals(2, response.records().size());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        """
                    <Track>
                      <Lat>39.9526</Lat>
                      <Lon>-75.1652</Lon>
                      <Foo>bar</Foo>
                      <Color>#0000FF</Color>
                    </Track>
                    <Track>
                      <Lat>39.9526</Lat>
                      <Lon>-75.1652</Lon>
                      <Extra>
                        <Nested>123</Nested>
                      </Extra>
                      <Color>#0000FF</Color>
                    </Track>
                    """
      })
  void fail_on_bad_xml_list(String xml) {
    MappingResponse response = map(xml, baseProfile());

    assertEquals(0, response.records().size());
    assertEquals(1, response.errors().size());
    assertEquals("XPATH_EVAL_ERROR", response.errors().getFirst().code());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        """
                    <Track>
                      <Lat>39.9526</Lat>
                      <Lon>-75.1652</Lon>
                      <Foo>bar</Foo>
                      <Props>
                        <Callsign>SAMPLE OBJECT</Callsign>
                        <Symbol>10031000001211000000</Symbol>
                      </Props>
                    </Track>
                    """
      })
  void fail_on_missing_mandatory_prop(String xml) {
    DynamicMapper profile =
        DynamicMapperBuilder.xml()
            .recordXPath("//Track") // Use double slash so it can find the node anywhere
            .addRule(
                MappingRuleBuilder.rule()
                    .xpath("Lat/text()")
                    .onMissing(OnMissing.ERROR)
                    .target(TargetField.GEOMETRY_LAT)
                    .build())
            .addRule(
                MappingRuleBuilder.rule()
                    .xpath("Lon/text()")
                    .onMissing(OnMissing.ERROR)
                    .target(TargetField.GEOMETRY_LON)
                    .build())
            .addRule(
                MappingRuleBuilder.rule()
                    .xpath("Props/Symbol/text()")
                    .onMissing(OnMissing.NULL)
                    .target(TargetField.ELEMENT_SYMBOL_SIDC)
                    .transforms(
                        TransformStepBuilder.trim(), TransformStepBuilder.replaceRegex("-", ""))
                    .build())
            .addRule(
                MappingRuleBuilder.rule()
                    .xpath("Props/ObjectId/text()")
                    .onMissing(OnMissing.ERROR)
                    .target(TargetField.OBJECT_ID)
                    .build())
            .addRule(
                MappingRuleBuilder.rule()
                    .xpath("Props/Callsign/text()")
                    .onMissing(OnMissing.ERROR)
                    .target(TargetField.OBJECT_ID)
                    .build())
            .build();
    MappingResponse response = map(xml, profile);

    assertEquals(0, response.records().size());
    assertEquals(1, response.errors().size());
    assertEquals("ON_MISSING_ERROR", response.errors().getFirst().code());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        """
                    <Track>
                      <Lat>39.9526</Lat>
                      <Lon>-75.1652</Lon>
                      <Foo>bar</Foo>
                      <Props>
                        <Symbol>10031000001211000000</Symbol>
                      </Props>
                    </Track>
                    """
      })
  void map_as_symbol_element(String xml) {
    DynamicMapper profile =
        DynamicMapperBuilder.xml()
            .recordXPath("//Track") // Use double slash so it can find the node anywhere
            .addRule(
                MappingRuleBuilder.rule()
                    .xpath("Lat/text()")
                    .onMissing(OnMissing.ERROR)
                    .target(TargetField.GEOMETRY_LAT)
                    .build())
            .addRule(
                MappingRuleBuilder.rule()
                    .xpath("Lon/text()")
                    .onMissing(OnMissing.ERROR)
                    .target(TargetField.GEOMETRY_LON)
                    .build())
            .addRule(
                MappingRuleBuilder.rule()
                    .xpath("Props/Symbol/text()")
                    .onMissing(OnMissing.NULL)
                    .target(TargetField.ELEMENT_SYMBOL_SIDC)
                    .build())
            .build();
    MappingResponse response = map(xml, profile);

    assertEquals(1, response.records().size());
    assertEquals(0, response.errors().size());
    List<Feature> features = response.records().getFirst().output();

    assertEquals(1, features.size());
    assertNotNull(features.getFirst());
    OpenCopProperties properties = OpenCopPropertiesParser.readFromFeature(features.getFirst());
    assertNotNull(properties);
    assertInstanceOf(Element.class, properties.getElement());
    assertEquals("10031000001211000000", properties.getElement().get(PointElement.SYMBOL));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        """
                    <Track>
                      <Lat>39.9526</Lat>
                      <Lon>-75.1652</Lon>
                      <Foo>bar</Foo>
                      <Props>
                        <Color>#0000FF</Color>
                      </Props>
                    </Track>
                    """
      })
  void map_as_point_element(String xml) {
    DynamicMapper profile =
        DynamicMapperBuilder.xml()
            .recordXPath("//Track") // Use double slash so it can find the node anywhere
            .addRule(
                MappingRuleBuilder.rule()
                    .xpath("Lat/text()")
                    .onMissing(OnMissing.ERROR)
                    .target(TargetField.GEOMETRY_LAT)
                    .build())
            .addRule(
                MappingRuleBuilder.rule()
                    .xpath("Lon/text()")
                    .onMissing(OnMissing.ERROR)
                    .target(TargetField.GEOMETRY_LON)
                    .build())
            .addRule(
                MappingRuleBuilder.rule()
                    .xpath("Props/Color/text()")
                    .onMissing(OnMissing.ERROR)
                    .target(TargetField.ELEMENT_POINT_COLOR)
                    .build())
            .build();
    MappingResponse response = map(xml, profile);

    assertEquals(1, response.records().size());
    assertEquals(0, response.errors().size());
    List<Feature> features = response.records().getFirst().output();

    assertEquals(1, features.size());
    assertNotNull(features.getFirst());
    OpenCopProperties properties = OpenCopPropertiesParser.readFromFeature(features.getFirst());
    assertNotNull(properties);
    assertInstanceOf(Element.class, properties.getElement());
    assertEquals("#0000FFb3", properties.getElement().get(PointElement.FILL_COLOR));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        """
                    <Track>
                      <Lat>        39,9526          </Lat>
                      <Lon>-75.1652-------------</Lon>
                      <Foo>bar</Foo>
                      <Props>
                        <Symbol>10-0-3-10-0-0-00-121100-00-00    </Symbol>
                        <Timez>
                            <Time>2024-01-15T05:30:45-05:00</Time>
                            <Stale>20240115 103045</Stale>
                            <Start>2024-01-15T10:30:45Z</Start>
                        </Timez>
                      </Props>
                    </Track>
                    """
      })
  void transform_trim_lat_regex_lon_transform_dates(String xml) {
    MappingRule[] rules = new MappingRule[6];
    rules[0] =
        MappingRuleBuilder.rule()
            .xpath("Lat/text()")
            .onMissing(OnMissing.ERROR)
            .target(TargetField.GEOMETRY_LAT)
            .transforms(TransformStepBuilder.trim(), TransformStepBuilder.replaceRegex(",", "."))
            .build();
    rules[1] =
        MappingRuleBuilder.rule()
            .xpath("Lon/text()")
            .onMissing(OnMissing.ERROR)
            .target(TargetField.GEOMETRY_LON)
            .transforms(TransformStepBuilder.replaceRegex("-", ""))
            .build();
    rules[2] =
        MappingRuleBuilder.rule()
            .xpath("Props/Symbol/text()")
            .onMissing(OnMissing.ERROR)
            .target(TargetField.ELEMENT_SYMBOL_SIDC)
            .transforms(TransformStepBuilder.trim(), TransformStepBuilder.replaceRegex("-", ""))
            .build();
    rules[3] =
        MappingRuleBuilder.rule()
            .xpath("Props/Timez/Time/text()")
            .onMissing(OnMissing.ERROR)
            .target(TargetField.UPDATED_AT)
            .transforms(TransformStepBuilder.parseDateTime())
            .build();
    rules[4] =
        MappingRuleBuilder.rule()
            .xpath("Props/Timez/Stale/text()")
            .onMissing(OnMissing.ERROR)
            .target(TargetField.STALE_AT)
            .transforms(
                TransformStepBuilder.op(TransformOperation.PARSE_DATE_TIME)
                    .format("yyyyMMdd HHmmss")
                    .value("America/New_York")
                    .build())
            .build();
    rules[5] =
        MappingRuleBuilder.rule()
            .xpath("Props/Timez/Start/text()")
            .onMissing(OnMissing.ERROR)
            .target(TargetField.START_AT)
            .transforms(TransformStepBuilder.parseDateTime())
            .build();
    MappingResponse response = map(xml, baseProfile(rules));

    assertEquals(1, response.records().size());
    assertEquals(0, response.errors().size());
    List<Feature> features = response.records().getFirst().output();

    assertEquals(1, features.size());
    assertNotNull(features.getFirst());
    OpenCopProperties properties = OpenCopPropertiesParser.readFromFeature(features.getFirst());
    assertNotNull(properties);
    assertEquals(
        39.9526d, ((Point) features.getFirst().getGeometry()).getCoordinates().getLatitude());
    assertEquals(
        75.1652d, ((Point) features.getFirst().getGeometry()).getCoordinates().getLongitude());
    assertEquals("10031000001211000000", properties.getElement().get(PointElement.SYMBOL));
    assertEquals(1705314645000L, properties.getTimestampz().getTime());
    assertEquals(1705332645000L, properties.getTimestampz().getStale());
    assertEquals(1705314645000L, properties.getTimestampz().getStart());
  }

  @Test
  void maps_timestamp_offset_from_same_payload_value() {
    String xml =
        """
                <Track>
                  <Lat>39.9526</Lat>
                  <Lon>75.1652</Lon>
                  <Color>#0000FF</Color>
                  <Props>
                    <Observed>2024-01-15T10:30:45Z</Observed>
                  </Props>
                </Track>
                """;

    DynamicMapper profile =
        baseProfileWithExtraRules(
            MappingRuleBuilder.rule()
                .xpath("Props/Observed/text()")
                .onMissing(OnMissing.ERROR)
                .target(TargetField.UPDATED_AT)
                .transforms(TransformStepBuilder.parseDateTime())
                .build(),
            MappingRuleBuilder.rule()
                .xpath("Props/Observed/text()")
                .onMissing(OnMissing.ERROR)
                .target(TargetField.START_AT)
                .transforms(TransformStepBuilder.parseDateTime())
                .build(),
            MappingRuleBuilder.rule()
                .xpath("Props/Observed/text()")
                .onMissing(OnMissing.ERROR)
                .target(TargetField.STALE_AT)
                .transforms(
                    TransformStepBuilder.parseDateTime(), TransformStepBuilder.addDuration("PT15M"))
                .build());

    MappingResponse response = map(xml, profile);

    assertEquals(1, response.records().size());
    assertEquals(0, response.errors().size());

    OpenCopProperties properties =
        OpenCopPropertiesParser.readFromFeature(response.records().getFirst().output().getFirst());

    assertEquals(1705314645000L, properties.getTimestampz().getTime());
    assertEquals(1705314645000L, properties.getTimestampz().getStart());
    assertEquals(1705315545000L, properties.getTimestampz().getStale());
  }

  @Test
  void maps_fixed_default_timestamp_when_source_value_is_missing() {
    String xml =
        """
                <Track>
                  <Lat>39.9526</Lat>
                  <Lon>75.1652</Lon>
                  <Color>#0000FF</Color>
                </Track>
                """;

    DynamicMapper profile =
        baseProfileWithExtraRules(
            MappingRuleBuilder.rule()
                .xpath("Missing/Updated/text()")
                .defaultValue("2024-01-15T10:30:45Z")
                .target(TargetField.UPDATED_AT)
                .transforms(TransformStepBuilder.parseDateTime())
                .build(),
            MappingRuleBuilder.rule()
                .xpath("Missing/Start/text()")
                .defaultValue("2024-01-15T10:30:45Z")
                .target(TargetField.START_AT)
                .transforms(TransformStepBuilder.parseDateTime())
                .build(),
            MappingRuleBuilder.rule()
                .xpath("Missing/Stale/text()")
                .defaultValue("2024-01-15T11:00:45Z")
                .target(TargetField.STALE_AT)
                .transforms(TransformStepBuilder.parseDateTime())
                .build());

    MappingResponse response = map(xml, profile);

    assertEquals(1, response.records().size());
    assertEquals(0, response.errors().size());

    OpenCopProperties properties =
        OpenCopPropertiesParser.readFromFeature(response.records().getFirst().output().getFirst());

    assertEquals(1705314645000L, properties.getTimestampz().getTime());
    assertEquals(1705314645000L, properties.getTimestampz().getStart());
    assertEquals(1705316445000L, properties.getTimestampz().getStale());
  }

  @Test
  void maps_default_now_and_adds_duration_when_source_value_is_missing() {
    String xml =
        """
                <Track>
                  <Lat>39.9526</Lat>
                  <Lon>75.1652</Lon>
                  <Color>#0000FF</Color>
                </Track>
                """;

    DynamicMapper profile =
        baseProfileWithExtraRules(
            MappingRuleBuilder.rule()
                .xpath("Missing/Updated/text()")
                .onMissing(OnMissing.ERROR)
                .target(TargetField.UPDATED_AT)
                .transforms(TransformStepBuilder.defaultNow())
                .build(),
            MappingRuleBuilder.rule()
                .xpath("Missing/Start/text()")
                .onMissing(OnMissing.ERROR)
                .target(TargetField.START_AT)
                .transforms(TransformStepBuilder.defaultNow())
                .build(),
            MappingRuleBuilder.rule()
                .xpath("Missing/Stale/text()")
                .onMissing(OnMissing.ERROR)
                .target(TargetField.STALE_AT)
                .transforms(
                    TransformStepBuilder.defaultNow(), TransformStepBuilder.addDuration("PT15M"))
                .build());

    Instant before = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    MappingResponse response = map(xml, profile);
    Instant after = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    assertEquals(1, response.records().size());
    assertEquals(0, response.errors().size());

    OpenCopProperties properties =
        OpenCopPropertiesParser.readFromFeature(response.records().getFirst().output().getFirst());

    Instant updatedAt = Instant.ofEpochMilli(properties.getTimestampz().getTime());
    Instant startAt = Instant.ofEpochMilli(properties.getTimestampz().getStart());
    Instant staleAt = Instant.ofEpochMilli(properties.getTimestampz().getStale());

    assertFalse(updatedAt.isBefore(before));
    assertFalse(updatedAt.isAfter(after));
    assertFalse(startAt.isBefore(before));
    assertFalse(startAt.isAfter(after));
    assertFalse(staleAt.isBefore(before.plusSeconds(15 * 60)));
    assertFalse(staleAt.isAfter(after.plusSeconds(15 * 60)));
  }

  private static DynamicMapper baseProfile() {
    return DynamicMapperBuilder.xml()
        .recordXPath("//Track") // Use double slash so it can find the node anywhere
        .addRule(
            MappingRuleBuilder.rule()
                .xpath("Lat/text()")
                .onMissing(OnMissing.ERROR)
                .target(TargetField.GEOMETRY_LAT)
                .build())
        .addRule(
            MappingRuleBuilder.rule()
                .xpath("Lon/text()")
                .onMissing(OnMissing.ERROR)
                .target(TargetField.GEOMETRY_LON)
                .build())
        .addRule(
            MappingRuleBuilder.rule()
                .xpath("Color/text()")
                .onMissing(OnMissing.ERROR)
                .target(TargetField.ELEMENT_POINT_COLOR)
                .build())
        .build();
  }

  private static DynamicMapper baseProfile(MappingRule... rules) {
    return DynamicMapperBuilder.xml()
        .recordXPath("//Track") // Use double slash so it can find the node anywhere
        .addRule(rules)
        .build();
  }

  private static DynamicMapper baseProfileWithExtraRules(MappingRule... rules) {
    return DynamicMapperBuilder.xml()
        .recordXPath("//Track")
        .addRule(
            MappingRuleBuilder.rule()
                .xpath("Lat/text()")
                .onMissing(OnMissing.ERROR)
                .target(TargetField.GEOMETRY_LAT)
                .build())
        .addRule(
            MappingRuleBuilder.rule()
                .xpath("Lon/text()")
                .onMissing(OnMissing.ERROR)
                .target(TargetField.GEOMETRY_LON)
                .build())
        .addRule(
            MappingRuleBuilder.rule()
                .xpath("Color/text()")
                .onMissing(OnMissing.ERROR)
                .target(TargetField.ELEMENT_POINT_COLOR)
                .build())
        .addRule(rules)
        .build();
  }

  private static final MappingProperties TEST_PROPERTIES =
      new MappingProperties(UUID.randomUUID(), UUID.randomUUID(), false);

  private MappingResponse map(String xml, DynamicMapper profile) {
    return facade.map(
        new MappingRequest(TEST_PROPERTIES, profile, xml.getBytes(StandardCharsets.UTF_8)));
  }
}
