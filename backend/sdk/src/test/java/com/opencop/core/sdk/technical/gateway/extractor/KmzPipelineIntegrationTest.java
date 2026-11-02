// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.extractor;

import static org.junit.jupiter.api.Assertions.*;

import com.opencop.core.sdk.model.mapper.MappingWarning;
import com.opencop.core.sdk.model.mapper.OnMissing;
import com.opencop.core.sdk.model.mapper.config.XmlFormatConfig;
import com.opencop.core.sdk.model.mapper.profile.DynamicMapper;
import com.opencop.core.sdk.model.mapper.rules.MappedTargetField;
import com.opencop.core.sdk.model.mapper.rules.MappingRule;
import com.opencop.core.sdk.model.mapper.rules.TargetField;
import com.opencop.core.sdk.model.mapper.selector.RecordSelector;
import com.opencop.core.sdk.model.mapper.source.XPathSource;
import com.opencop.core.sdk.technical.mapper.MappingProperties;
import com.opencop.core.sdk.technical.mapper.engine.FeatureBuilder;
import com.opencop.core.sdk.technical.mapper.engine.extractor.kml.KmlExtractor;
import com.opencop.core.sdk.technical.mapper.engine.session.SessionField;
import com.opencop.core.sdk.technical.model.opencop.elements.MediaIconElement;
import com.opencop.core.sdk.technical.model.opencop.parser.OpenCopPropertiesParser;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import net.sf.saxon.s9api.XdmNode;
import org.geojson.Feature;
import org.geojson.GeoJsonObject;
import org.geojson.Point;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * Full-chain integration test using a project-authored synthetic KMZ. Exercises the entire data
 * path from generated KMZ bytes to GeoJSON media-icon features with opencop:media: hrefs:
 *
 * <pre>
 *   KMZ bytes
 *     → KmzPipeline (ZipSiphon + KmzAssetResolver)   [Core side]
 *     → KmlExtractor (parse KML, extract iconHref)    [Processor side]
 *     → manifest lookup (rawHref → opencop:media:{id}) [Processor side]
 *     → FeatureBuilder (produce GeoJSON Feature)      [Processor side]
 * </pre>
 *
 * The archive is built deterministically in this test from the public fixture contract.
 */
class KmzPipelineIntegrationTest {

  private static final String KML_NS = "http://www.opengis.net/kml/2.2";
  private static final String GX_NS = "http://www.google.com/kml/ext/2.2";
  private static final UUID MEDIA_ID = UUID.fromString("00000000-0000-4000-8000-000000000103");
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String ICON_PATH = "icons/sample-marker.png";
  private static final int BACKGROUND_RGB = new Color(31, 111, 168).getRGB();
  private static final int MARKER_RGB = Color.WHITE.getRGB();

  private static byte[] kmzBytes;
  private static KmzPipeline.Result pipelineResult;

  @BeforeAll
  static void runCorePipeline() throws Exception {
    kmzBytes = syntheticKmz();
    pipelineResult = KmzPipeline.process(kmzBytes, ignoredPath -> MEDIA_ID);
  }

  @Test
  void builds_a_deterministic_archive_with_exactly_one_kml_and_one_geometric_icon()
      throws Exception {
    assertArrayEquals(kmzBytes, syntheticKmz());
    assertEquals(List.of("doc.kml", ICON_PATH), entryNames(kmzBytes));

    byte[] extractedIcon = pipelineResult.siphoned().extractedByPath().get(ICON_PATH).bytes();
    BufferedImage icon = ImageIO.read(new ByteArrayInputStream(extractedIcon));
    assertNotNull(icon);
    assertEquals(8, icon.getWidth());
    assertEquals(8, icon.getHeight());
    assertEquals(BACKGROUND_RGB, icon.getRGB(0, 0));
    assertEquals(MARKER_RGB, icon.getRGB(3, 3));
  }

  @Test
  void pipeline_extracts_the_geometric_icon_and_resolves_its_local_href() {
    assertEquals(Set.of(ICON_PATH), pipelineResult.siphoned().extractedByPath().keySet());
    assertEquals(1, pipelineResult.manifest().size());

    KmzIconRef ref = pipelineResult.manifest().getFirst();
    assertEquals("doc.kml", ref.kmlPath());
    assertEquals(ICON_PATH, ref.rawHref());
    assertEquals(ICON_PATH, ref.resolvedPath());
    assertEquals(MEDIA_ID, ref.mediaId());
    assertEquals(KmzIconRef.Status.RESOLVED, ref.status());
  }

  /**
   * Feed the generated KML through KmlExtractor, verify its local icon href matches the Core
   * manifest, then chain into FeatureBuilder and assert the final feature is a media icon whose
   * href is opencop:media:.
   */
  @Test
  void full_chain_kmz_to_media_icon_feature() throws Exception {
    byte[] kmlBytes = extractFirstKmlEntry(pipelineResult.siphoned().strippedZip());
    assertNotNull(kmlBytes, "Stripped ZIP should contain a KML entry");

    // --- Processor side: KmlExtractor parses placemarks ---
    DynamicMapper profile = profileWithRecordXPath("//kml:Placemark");
    KmlExtractor extractor = KmlExtractor.fromProfile(profile);
    List<XdmNode> records = extractor.selectRecords(kmlBytes, profile);
    assertEquals(1, records.size(), "Synthetic KML should contain exactly one placemark");

    String kmlPath = pipelineResult.manifest().getFirst().kmlPath();
    MappingRule iconHrefRule = ruleWithXPath("kmlstyle:iconHref");
    MappingRule geojsonRule = ruleWithXPath("kmlgeom:geojson");
    MappingRule nameRule = ruleWithXPath("kml:name/text()");

    XdmNode record = records.getFirst();
    String iconHref = extractor.selectString(record, iconHrefRule);
    assertEquals(ICON_PATH, iconHref);

    KmzIconRef ref = KmzIconRef.lookup(pipelineResult.manifest(), kmlPath, iconHref);
    assertNotNull(ref, "Processor href '%s' not found in Core manifest".formatted(iconHref));
    String symbolValue = ref.toSymbolValue();
    assertEquals("opencop:media:" + MEDIA_ID, symbolValue);

    String geojson = extractor.selectString(record, geojsonRule);
    assertNotNull(geojson);
    GeoJsonObject geo = MAPPER.readValue(geojson, GeoJsonObject.class);
    Point point = assertInstanceOf(Point.class, geo);
    assertEquals(-100.30d, point.getCoordinates().getLongitude());
    assertEquals(40.30d, point.getCoordinates().getLatitude());
    assertEquals(0d, point.getCoordinates().getAltitude());

    String displayName = extractor.selectString(record, nameRule);
    assertEquals("SAMPLE KMZ POINT", displayName);
    Map<String, SessionField> fields = new HashMap<>();
    fields.put(TargetField.GEOMETRY.path(), sessionField(TargetField.GEOMETRY, geo));
    fields.put(
        TargetField.ELEMENT_ICON_LOCATION.path(),
        sessionField(TargetField.ELEMENT_ICON_LOCATION, symbolValue));
    fields.put(
        TargetField.DISPLAY_NAME.path(), sessionField(TargetField.DISPLAY_NAME, displayName));

    MappingProperties props =
        new MappingProperties(
            UUID.fromString("00000000-0000-4000-8000-000000000203"),
            UUID.fromString("00000000-0000-4000-8000-000000000103"),
            false);
    List<MappingWarning> warnings = new ArrayList<>();
    List<Feature> features = new FeatureBuilder(fields, props, warnings, 0).build();

    assertEquals(1, features.size());
    Feature feature = features.getFirst();
    var openCopProps = OpenCopPropertiesParser.readFromFeature(feature);

    assertEquals("SAMPLE KMZ POINT", openCopProps.getDisplayName());
    assertEquals("media-icon", openCopProps.getElement().getType());
    assertEquals("opencop:media:" + MEDIA_ID, openCopProps.getElement().get(MediaIconElement.HREF));
  }

  @Test
  void processor_hrefs_and_core_manifest_are_bidirectionally_consistent() throws Exception {
    byte[] kmlBytes = extractFirstKmlEntry(pipelineResult.siphoned().strippedZip());
    assertNotNull(kmlBytes);

    DynamicMapper profile = profileWithRecordXPath("//kml:Placemark");
    KmlExtractor extractor = KmlExtractor.fromProfile(profile);
    List<XdmNode> records = extractor.selectRecords(kmlBytes, profile);

    // Collect all unique iconHrefs the Processor extracts
    Set<String> processorHrefs = new HashSet<>();
    MappingRule iconHrefRule = ruleWithXPath("kmlstyle:iconHref");
    for (XdmNode record : records) {
      String href = extractor.selectString(record, iconHrefRule);
      if (href != null) processorHrefs.add(href);
    }
    assertEquals(Set.of(ICON_PATH), processorHrefs);

    String kmlPath = pipelineResult.manifest().getFirst().kmlPath();

    // Every Processor href is in the Core manifest
    for (String href : processorHrefs) {
      assertNotNull(
          KmzIconRef.lookup(pipelineResult.manifest(), kmlPath, href),
          "Processor href '%s' missing from Core manifest".formatted(href));
    }

    // Every local Core manifest entry was extracted by the Processor
    for (KmzIconRef ref : pipelineResult.manifest()) {
      assertTrue(
          processorHrefs.contains(ref.rawHref()),
          "Core manifest href '%s' not extracted by Processor".formatted(ref.rawHref()));
    }
  }

  // -- Helpers --

  private static byte[] syntheticKmz() throws Exception {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(output)) {
      addEntry(zip, "doc.kml", syntheticKml().getBytes(StandardCharsets.UTF_8));
      addEntry(zip, ICON_PATH, geometricIconPng());
    }
    return output.toByteArray();
  }

  private static String syntheticKml() {
    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <kml xmlns="http://www.opengis.net/kml/2.2">
          <Document>
            <Style id="sample-marker-style">
              <IconStyle>
                <Icon><href>icons/sample-marker.png</href></Icon>
              </IconStyle>
            </Style>
            <Placemark>
              <name>SAMPLE KMZ POINT</name>
              <styleUrl>#sample-marker-style</styleUrl>
              <Point><coordinates>-100.30,40.30,0</coordinates></Point>
            </Placemark>
          </Document>
        </kml>
        """;
  }

  private static byte[] geometricIconPng() throws Exception {
    BufferedImage icon = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
    Graphics2D graphics = icon.createGraphics();
    try {
      graphics.setColor(new Color(BACKGROUND_RGB, true));
      graphics.fillRect(0, 0, icon.getWidth(), icon.getHeight());
      graphics.setColor(new Color(MARKER_RGB, true));
      graphics.fillOval(2, 2, 4, 4);
    } finally {
      graphics.dispose();
    }

    ByteArrayOutputStream output = new ByteArrayOutputStream();
    assertTrue(ImageIO.write(icon, "png", output));
    return output.toByteArray();
  }

  private static void addEntry(ZipOutputStream zip, String path, byte[] content) throws Exception {
    ZipEntry entry = new ZipEntry(path);
    entry.setTime(0L);
    zip.putNextEntry(entry);
    zip.write(content);
    zip.closeEntry();
  }

  private static List<String> entryNames(byte[] zipBytes) throws Exception {
    List<String> names = new ArrayList<>();
    try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        if (!entry.isDirectory()) {
          names.add(entry.getName());
        }
      }
    }
    return names;
  }

  private static byte[] extractFirstKmlEntry(byte[] zipBytes) throws Exception {
    try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        if (!entry.isDirectory()
            && entry.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".kml")) {
          return zis.readAllBytes();
        }
      }
    }
    return null;
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
