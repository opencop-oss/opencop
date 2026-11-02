// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.mapper.engine.extractor.xml;

import static org.junit.jupiter.api.Assertions.*;

import com.opencop.core.sdk.model.mapper.OnMissing;
import com.opencop.core.sdk.model.mapper.config.XmlFormatConfig;
import com.opencop.core.sdk.model.mapper.profile.DynamicMapper;
import com.opencop.core.sdk.model.mapper.rules.MappingRule;
import com.opencop.core.sdk.model.mapper.selector.RecordSelector;
import com.opencop.core.sdk.model.mapper.source.XPathSource;
import com.opencop.core.sdk.technical.mapper.engine.MapperEngineException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.sf.saxon.s9api.XdmNode;
import org.junit.jupiter.api.Test;

class XmlExtractorTest {

  private static final String XML =
      """
        <Tracks>
            <Track>
              <Lat>39.9526</Lat>
              <Lon>-75.1652</Lon>
              <Foo>bar</Foo>
            </Track>
            <Track>
              <Lat>46.0000</Lat>
              <Lon>-70.0000</Lon>
              <Extra>
                <Nested>123</Nested>
              </Extra>
            </Track>
        </Tracks>
        """;

  @Test
  void select_records_returns_all_track_nodes() throws MapperEngineException {
    DynamicMapper profile = profileWithRecordXPath("/Tracks/Track");
    XmlExtractor extractor = XmlExtractor.fromProfile(profile);

    List<XdmNode> records = extractor.selectRecords(bytes(XML), profile);

    assertEquals(2, records.size());
    assertEquals("Track", records.get(0).getNodeName().getLocalName());
    assertEquals("Track", records.get(1).getNodeName().getLocalName());
  }

  @Test
  void select_string_extracts_value_from_record_context() throws MapperEngineException {
    DynamicMapper profile = profileWithRecordXPath("/Tracks/Track");
    XmlExtractor extractor = XmlExtractor.fromProfile(profile);

    List<XdmNode> records = extractor.selectRecords(bytes(XML), profile);

    MappingRule latRule = ruleWithXPath("Lat/text()");
    String lat = extractor.selectString(records.getFirst(), latRule);

    assertEquals("39.9526", lat);
  }

  @Test
  void select_string_missing_value_returns_null() throws MapperEngineException {
    DynamicMapper profile = profileWithRecordXPath("/Tracks/Track");
    XmlExtractor extractor = XmlExtractor.fromProfile(profile);

    XdmNode first = extractor.selectRecords(bytes(XML), profile).getFirst();

    MappingRule missingRule = ruleWithXPath("DoesNotExist/text()");
    String out = extractor.selectString(first, missingRule);

    assertNull(out);
  }

  @Test
  void select_records_invalid_record_xpath_throws_xpath_eval_error() {
    DynamicMapper profile = profileWithRecordXPath("//*[" /* invalid xpath */);
    XmlExtractor extractor = XmlExtractor.fromProfile(profile);

    MapperEngineException ex =
        assertThrows(
            MapperEngineException.class, () -> extractor.selectRecords(bytes(XML), profile));

    assertEquals("XPATH_EVAL_ERROR", ex.getCode());
    assertTrue(ex.getMessage().contains("Failed to evaluate XPath"));
  }

  @Test
  void select_string_invalid_field_xpath_throws_xpath_compile_error() throws MapperEngineException {
    DynamicMapper profile = profileWithRecordXPath("/Tracks/Track");
    XmlExtractor extractor = XmlExtractor.fromProfile(profile);

    XdmNode first = extractor.selectRecords(bytes(XML), profile).getFirst();

    MappingRule badRule = ruleWithXPath("//*[" /* invalid xpath */);

    MapperEngineException ex =
        assertThrows(MapperEngineException.class, () -> extractor.selectString(first, badRule));

    assertEquals("XPATH_COMPILE_ERROR", ex.getCode());
    assertTrue(ex.getMessage().contains("compiling the xpath"));
  }

  @Test
  void to_bytes_serializes_record_node() throws MapperEngineException {
    DynamicMapper profile = profileWithRecordXPath("/Tracks/Track");
    XmlExtractor extractor = XmlExtractor.fromProfile(profile);

    XdmNode first = extractor.selectRecords(bytes(XML), profile).getFirst();

    byte[] recordBytes = extractor.toBytes(first);

    assertNotNull(recordBytes);
    String asText = new String(recordBytes, StandardCharsets.UTF_8);
    assertTrue(asText.contains("<Track>"));
    assertTrue(asText.contains("<Foo>bar</Foo>"));
  }

  // ----- helpers -----

  private static byte[] bytes(String s) {
    return s.getBytes(StandardCharsets.UTF_8);
  }

  private static DynamicMapper profileWithRecordXPath(String recordXPath) {
    // Adjust constructors to your actual records if names differ.
    return new DynamicMapper(
        UUID.randomUUID(),
        true,
        new XmlFormatConfig(Map.of(), true),
        new RecordSelector(new XPathSource(recordXPath), null),
        List.of());
  }

  private static MappingRule ruleWithXPath(String xpath) {
    return new MappingRule(
        UUID.randomUUID(), true, null, null, new XPathSource(xpath), null, OnMissing.ERROR, null);
  }
}
