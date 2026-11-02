// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.mapper.engine.extractor.kml;

import static org.junit.jupiter.api.Assertions.*;

import com.opencop.core.sdk.model.mapper.OnMissing;
import com.opencop.core.sdk.model.mapper.config.XmlFormatConfig;
import com.opencop.core.sdk.model.mapper.profile.DynamicMapper;
import com.opencop.core.sdk.model.mapper.rules.MappingRule;
import com.opencop.core.sdk.model.mapper.selector.RecordSelector;
import com.opencop.core.sdk.model.mapper.source.XPathSource;
import com.opencop.core.sdk.technical.mapper.engine.MapperEngineException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.sf.saxon.s9api.XdmNode;
import org.geojson.Point;
import org.junit.jupiter.api.Test;

class KmlExtractorTest {

  private static final String KML_NS = "http://www.opengis.net/kml/2.2";
  private static final String GX_NS = "http://www.google.com/kml/ext/2.2";

  @Test
  void select_records_returns_placemarks_and_allows_xpath_fields() throws MapperEngineException {
    String xml =
        """
        <kml xmlns=\"http://www.opengis.net/kml/2.2\">
          <Document>
            <Placemark>
              <name>Place A</name>
              <Point><coordinates>-75.1652,39.9526,0</coordinates></Point>
            </Placemark>
            <Placemark>
              <name>Place B</name>
              <Point><coordinates>-70,46,0</coordinates></Point>
            </Placemark>
          </Document>
        </kml>
        """;

    DynamicMapper profile = profileWithRecordXPath("//kml:Placemark");
    KmlExtractor extractor = KmlExtractor.fromProfile(profile);

    List<XdmNode> records = extractor.selectRecords(bytes(xml), profile);

    assertEquals(2, records.size());

    MappingRule nameRule = ruleWithXPath("kml:name/text()");
    assertEquals("Place A", extractor.selectString(records.getFirst(), nameRule));
  }

  @Test
  void select_string_kmlgeom_geojson_for_point() throws MapperEngineException {
    String xml =
        """
        <kml xmlns=\"http://www.opengis.net/kml/2.2\">
          <Placemark>
            <name>Point A</name>
            <Point><coordinates>-75.1652,39.9526,0</coordinates></Point>
          </Placemark>
        </kml>
        """;

    DynamicMapper profile = profileWithRecordXPath("//kml:Placemark");
    KmlExtractor extractor = KmlExtractor.fromProfile(profile);

    XdmNode record = extractor.selectRecords(bytes(xml), profile).getFirst();

    String geojson = extractor.selectString(record, ruleWithXPath("kmlgeom:geojson"));

    assertNotNull(geojson);
    assertTrue(geojson.contains("\"type\":\"Point\""));
    assertTrue(geojson.contains("-75.1652"));
    assertTrue(geojson.contains("39.9526"));
  }

  @Test
  void style_inline_is_extracted() throws MapperEngineException {
    String xml =
        """
        <kml xmlns=\"http://www.opengis.net/kml/2.2\">
          <Placemark>
            <name>Styled Inline</name>
            <Style>
              <LineStyle>
                <color>ff0000ff</color>
                <width>2.5</width>
              </LineStyle>
              <PolyStyle>
                <color>7f00ff00</color>
              </PolyStyle>
            </Style>
            <Point><coordinates>-70,46,0</coordinates></Point>
          </Placemark>
        </kml>
        """;

    DynamicMapper profile = profileWithRecordXPath("//kml:Placemark");
    KmlExtractor extractor = KmlExtractor.fromProfile(profile);

    XdmNode record = extractor.selectRecords(bytes(xml), profile).getFirst();

    assertEquals(
        "#FF0000FF", extractor.selectString(record, ruleWithXPath("kmlstyle:strokeColor")));
    assertEquals("2.5", extractor.selectString(record, ruleWithXPath("kmlstyle:strokeWidth")));
    assertEquals("#00FF007F", extractor.selectString(record, ruleWithXPath("kmlstyle:fillColor")));
  }

  @Test
  void style_stylemap_normal_is_resolved() throws MapperEngineException {
    String xml =
        """
        <kml xmlns=\"http://www.opengis.net/kml/2.2\">
          <Document>
            <Style id=\"style1\">
              <LineStyle>
                <color>ff00ff00</color>
                <width>1</width>
              </LineStyle>
            </Style>
            <StyleMap id=\"map1\">
              <Pair>
                <key>normal</key>
                <styleUrl>#style1</styleUrl>
              </Pair>
              <Pair>
                <key>highlight</key>
                <styleUrl>#style2</styleUrl>
              </Pair>
            </StyleMap>
            <Placemark>
              <name>Uses StyleMap</name>
              <styleUrl>#map1</styleUrl>
              <Point><coordinates>-70,46,0</coordinates></Point>
            </Placemark>
          </Document>
        </kml>
        """;

    DynamicMapper profile = profileWithRecordXPath("//kml:Placemark");
    KmlExtractor extractor = KmlExtractor.fromProfile(profile);

    XdmNode record = extractor.selectRecords(bytes(xml), profile).getFirst();

    assertEquals(
        "#00FF00FF", extractor.selectString(record, ruleWithXPath("kmlstyle:strokeColor")));
  }

  @Test
  void style_external_style_url_is_ignored() throws MapperEngineException {
    String xml =
        """
        <kml xmlns=\"http://www.opengis.net/kml/2.2\">
          <Placemark>
            <name>External Style</name>
            <styleUrl>http://example.com/styles.kml#style1</styleUrl>
            <Point><coordinates>-70,46,0</coordinates></Point>
          </Placemark>
        </kml>
        """;

    DynamicMapper profile = profileWithRecordXPath("//kml:Placemark");
    KmlExtractor extractor = KmlExtractor.fromProfile(profile);

    XdmNode record = extractor.selectRecords(bytes(xml), profile).getFirst();

    assertNull(extractor.selectString(record, ruleWithXPath("kmlstyle:strokeColor")));
    assertNull(extractor.selectString(record, ruleWithXPath("kmlstyle:strokeWidth")));
  }

  @Test
  void style_invalid_color_is_null() throws MapperEngineException {
    String xml =
        """
        <kml xmlns=\"http://www.opengis.net/kml/2.2\">
          <Placemark>
            <Style>
              <LineStyle>
                <color>ff00ff</color>
                <width>1</width>
              </LineStyle>
              <PolyStyle>
                <color>ff00ff</color>
              </PolyStyle>
            </Style>
            <Point><coordinates>-70,46,0</coordinates></Point>
          </Placemark>
        </kml>
        """;

    DynamicMapper profile = profileWithRecordXPath("//kml:Placemark");
    KmlExtractor extractor = KmlExtractor.fromProfile(profile);

    XdmNode record = extractor.selectRecords(bytes(xml), profile).getFirst();

    assertEquals(
        "#FF00FFFF", extractor.selectString(record, ruleWithXPath("kmlstyle:strokeColor")));
    assertEquals("#FF00FFFF", extractor.selectString(record, ruleWithXPath("kmlstyle:fillColor")));
    assertNull(extractor.selectString(record, ruleWithXPath("kmlstyle:fillOpacity")));
  }

  @Test
  void geometry_missing_returns_null() throws MapperEngineException {
    String xml =
        """
        <kml xmlns=\"http://www.opengis.net/kml/2.2\">
          <Placemark>
            <name>NoGeom</name>
          </Placemark>
        </kml>
        """;

    DynamicMapper profile = profileWithRecordXPath("//kml:Placemark");
    KmlExtractor extractor = KmlExtractor.fromProfile(profile);

    XdmNode record = extractor.selectRecords(bytes(xml), profile).getFirst();

    assertNull(extractor.selectString(record, ruleWithXPath("kmlgeom:geojson")));
  }

  @Test
  void invalid_geometry_throws_mapper_engine_exception() {
    String xml =
        """
        <kml xmlns=\"http://www.opengis.net/kml/2.2\">
          <Placemark>
            <name>BadGeom</name>
            <Point><coordinates>bad,coords</coordinates></Point>
          </Placemark>
        </kml>
        """;

    DynamicMapper profile = profileWithRecordXPath("//kml:Placemark");
    KmlExtractor extractor = KmlExtractor.fromProfile(profile);

    MapperEngineException ex =
        assertThrows(
            MapperEngineException.class, () -> extractor.selectRecords(bytes(xml), profile));

    assertEquals("KML_GEOMETRY_ERROR", ex.getCode());
  }

  @Test
  void synthetic_spaced_point_coordinates_parse() throws Exception {
    byte[] kml = syntheticSpacedPoints();
    String kmlText = new String(kml, StandardCharsets.UTF_8);
    assertEquals(1, occurrences(kmlText, "<StyleMap "));
    assertTrue(kmlText.contains("-100.10, 40.10, 0"));
    assertTrue(kmlText.contains("-100.20, 40.20, 0"));

    DynamicMapper profile = profileWithRecordXPath("//kml:Placemark");
    KmlExtractor extractor = KmlExtractor.fromProfile(profile);

    List<XdmNode> records = extractor.selectRecords(kml, profile);

    assertEquals(2, records.size(), "Expected both synthetic placemarks to parse");

    List<String> expectedNames = List.of("SAMPLE POINT A", "SAMPLE POINT B");
    List<double[]> expectedCoordinates =
        List.of(new double[] {-100.10d, 40.10d, 0d}, new double[] {-100.20d, 40.20d, 0d});
    tools.jackson.databind.ObjectMapper objectMapper = new tools.jackson.databind.ObjectMapper();
    for (int index = 0; index < records.size(); index++) {
      XdmNode record = records.get(index);
      assertEquals(
          expectedNames.get(index),
          extractor.selectString(record, ruleWithXPath("kml:name/text()")));
      String geoJson = extractor.selectString(record, ruleWithXPath("kmlgeom:geojson"));
      assertNotNull(geoJson, "Expected point GeoJSON for synthetic placemark");
      Point point = objectMapper.readValue(geoJson, Point.class);
      double[] expected = expectedCoordinates.get(index);
      assertEquals(expected[0], point.getCoordinates().getLongitude());
      assertEquals(expected[1], point.getCoordinates().getLatitude());
      assertEquals(expected[2], point.getCoordinates().getAltitude());

      String iconHref = extractor.selectString(record, ruleWithXPath("kmlstyle:iconHref"));
      assertEquals("icons/sample-marker.png", iconHref);
    }
  }

  @Test
  void synthetic_spaced_point_coordinates_full_mapping_session() throws Exception {
    byte[] kml = syntheticSpacedPoints();

    byte[] profileJson =
        KmlExtractorTest.class
            .getClassLoader()
            .getResourceAsStream("profiles/mappers/kml+kmz.v1.json")
            .readAllBytes();
    tools.jackson.databind.ObjectMapper om = new tools.jackson.databind.ObjectMapper();
    var profileNode = om.readTree(profileJson);
    DynamicMapper dynMapper = om.treeToValue(profileNode.get("dynamicMapper"), DynamicMapper.class);

    KmlExtractor extractor = KmlExtractor.fromProfile(dynMapper);
    com.opencop.core.sdk.technical.mapper.engine.transformer.TransformEngine transformEngine =
        (input, steps) -> input;
    var props =
        new com.opencop.core.sdk.technical.mapper.MappingProperties(
            UUID.randomUUID(), null, false, List.of());
    var session =
        new com.opencop.core.sdk.technical.mapper.engine.session.MappingSession<>(
            transformEngine, dynMapper, extractor, props);

    com.opencop.core.sdk.model.mapper.MappingResponse response = session.execute(kml);

    assertEquals(2, response.records().size(), "Expected both synthetic placemarks to map cleanly");
    assertTrue(response.errors().isEmpty(), "Expected no errors, got: " + response.errors());
  }

  @Test
  void missing_poly_style_color_parses_generic_regions() throws Exception {
    byte[] kml = missingPolyStyleColor();
    String kmlText = new String(kml, StandardCharsets.UTF_8);

    long missingColorStyles =
        java.util.regex.Pattern.compile("<PolyStyle>\\s*<fill>0</fill>\\s*</PolyStyle>")
            .matcher(kmlText)
            .results()
            .count();
    assertEquals(2, missingColorStyles, "Expected two PolyStyle entries without a color");

    DynamicMapper profile = profileWithRecordXPath("//kml:Placemark");
    KmlExtractor extractor = KmlExtractor.fromProfile(profile);

    List<XdmNode> records = extractor.selectRecords(kml, profile);
    assertEquals(3, records.size(), "Expected all three generic regions");

    MappingRule geomRule = ruleWithXPath("kmlgeom:geojson");
    String geoJson = extractor.selectString(records.getFirst(), 0, geomRule);
    assertNotNull(geoJson, "Expected GeoJSON geometry for first placemark");
    assertTrue(geoJson.contains("coordinates"), "GeoJSON should contain coordinates");

    MappingRule nameRule = ruleWithXPath("kml:name/text()");
    assertEquals("Region A", extractor.selectString(records.get(0), nameRule));
    assertEquals("Region B", extractor.selectString(records.get(1), nameRule));
    assertEquals("Region C", extractor.selectString(records.get(2), nameRule));
  }

  /**
   * Full MappingSession with the real KML profile — catches errors the extractor-only test misses.
   */
  @Test
  void missing_poly_style_color_full_mapping_session() throws Exception {
    byte[] kml = missingPolyStyleColor();

    byte[] profileJson =
        KmlExtractorTest.class
            .getClassLoader()
            .getResourceAsStream("profiles/mappers/kml+kmz.v1.json")
            .readAllBytes();
    tools.jackson.databind.ObjectMapper om = new tools.jackson.databind.ObjectMapper();
    var profileNode = om.readTree(profileJson);
    DynamicMapper dynMapper = om.treeToValue(profileNode.get("dynamicMapper"), DynamicMapper.class);

    KmlExtractor extractor = KmlExtractor.fromProfile(dynMapper);
    com.opencop.core.sdk.technical.mapper.engine.transformer.TransformEngine transformEngine =
        (input, steps) -> input;
    var props =
        new com.opencop.core.sdk.technical.mapper.MappingProperties(
            UUID.randomUUID(), null, false, List.of());
    var session =
        new com.opencop.core.sdk.technical.mapper.engine.session.MappingSession<>(
            transformEngine, dynMapper, extractor, props);

    com.opencop.core.sdk.model.mapper.MappingResponse response = session.execute(kml);

    assertEquals(3, response.records().size(), "Expected all three generic regions to map");
    assertTrue(response.errors().isEmpty(), "Expected no errors, got: " + response.errors());
  }

  private static byte[] bytes(String s) {
    return s.getBytes(StandardCharsets.UTF_8);
  }

  private static byte[] syntheticSpacedPoints() throws Exception {
    try (InputStream input =
        KmlExtractorTest.class
            .getClassLoader()
            .getResourceAsStream("kml/synthetic-spaced-points.kml")) {
      assertNotNull(input, "Synthetic spaced-point KML fixture must exist");
      return input.readAllBytes();
    }
  }

  private static byte[] missingPolyStyleColor() throws Exception {
    try (InputStream input =
        KmlExtractorTest.class
            .getClassLoader()
            .getResourceAsStream("kml/missing-poly-style-color.kml")) {
      assertNotNull(input, "Missing-PolyStyle-color KML fixture must exist");
      return input.readAllBytes();
    }
  }

  private static int occurrences(String value, String token) {
    return value.split(java.util.regex.Pattern.quote(token), -1).length - 1;
  }

  private static DynamicMapper profileWithRecordXPath(String recordXPath) {
    return new DynamicMapper(
        UUID.randomUUID(),
        true,
        new XmlFormatConfig(Map.of("kml", KML_NS, "gx", GX_NS), true),
        new RecordSelector(new XPathSource(recordXPath), OnMissing.ERROR),
        List.of());
  }

  private static MappingRule ruleWithXPath(String xpath) {
    return new MappingRule(
        UUID.randomUUID(), true, null, null, new XPathSource(xpath), null, OnMissing.NULL, null);
  }
}
