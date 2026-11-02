// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.mapper.engine.extractor.csv;

import static org.junit.jupiter.api.Assertions.*;

import com.opencop.core.sdk.model.mapper.OnMissing;
import com.opencop.core.sdk.model.mapper.config.CsvFormatConfig;
import com.opencop.core.sdk.model.mapper.profile.DynamicMapper;
import com.opencop.core.sdk.model.mapper.rules.MappingRule;
import com.opencop.core.sdk.model.mapper.selector.RecordSelector;
import com.opencop.core.sdk.model.mapper.source.CsvColumnSource;
import com.opencop.core.sdk.model.mapper.source.XPathSource;
import com.opencop.core.sdk.technical.mapper.engine.MapperEngineException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CsvExtractorTest {

  private static final String CSV_WITH_HEADER =
      """
        Lat,Lon,Foo
        39.9526,-75.1652,bar
        46.0000,-70.0000,"hello, world"
        """;

  private static final String CSV_NO_HEADER =
      """
        39.9526,-75.1652,bar
        46.0000,-70.0000,baz
        """;

  @Test
  void select_records_with_header_maps_rows_to_header_keys() throws MapperEngineException {
    DynamicMapper profile = profileCsv(true, ',', '"');
    CsvExtractor extractor = CsvExtractor.fromProfile(profile);

    List<Map<String, String>> records = extractor.selectRecords(bytes(CSV_WITH_HEADER), profile);

    assertEquals(2, records.size());
    assertEquals("39.9526", records.getFirst().get("Lat"));
    assertEquals("-75.1652", records.getFirst().get("Lon"));
    assertEquals("bar", records.getFirst().get("Foo"));

    assertEquals("46.0000", records.get(1).get("Lat"));
    assertEquals("-70.0000", records.get(1).get("Lon"));
    assertEquals("hello, world", records.get(1).get("Foo"));
  }

  @Test
  void select_records_without_header_uses_numeric_keys() throws MapperEngineException {
    DynamicMapper profile = profileCsv(false, ',', '"');
    CsvExtractor extractor = CsvExtractor.fromProfile(profile);

    List<Map<String, String>> records = extractor.selectRecords(bytes(CSV_NO_HEADER), profile);

    assertEquals(2, records.size());
    assertEquals("39.9526", records.getFirst().get("0"));
    assertEquals("-75.1652", records.getFirst().get("1"));
    assertEquals("bar", records.getFirst().get("2"));
  }

  @Test
  void select_string_by_column_name() throws MapperEngineException {
    DynamicMapper profile = profileCsv(true, ',', '"');
    CsvExtractor extractor = CsvExtractor.fromProfile(profile);

    Map<String, String> first = extractor.selectRecords(bytes(CSV_WITH_HEADER), profile).getFirst();

    MappingRule lonRule = ruleWithColumn("Lon", null);
    String lon = extractor.selectString(first, lonRule);

    assertEquals("-75.1652", lon);
  }

  @Test
  void select_string_by_index_when_no_header() throws MapperEngineException {
    DynamicMapper profile = profileCsv(false, ',', '"');
    CsvExtractor extractor = CsvExtractor.fromProfile(profile);

    Map<String, String> first = extractor.selectRecords(bytes(CSV_NO_HEADER), profile).getFirst();

    MappingRule lonRule = ruleWithColumn(null, 1);
    String lon = extractor.selectString(first, lonRule);

    assertEquals("-75.1652", lon);
  }

  @Test
  void select_string_missing_column_returns_null() throws MapperEngineException {
    DynamicMapper profile = profileCsv(true, ',', '"');
    CsvExtractor extractor = CsvExtractor.fromProfile(profile);

    Map<String, String> first = extractor.selectRecords(bytes(CSV_WITH_HEADER), profile).getFirst();

    MappingRule missingRule = ruleWithColumn("DoesNotExist", null);
    String out = extractor.selectString(first, missingRule);

    assertNull(out);
  }

  @Test
  void parse_line_handles_quoted_delimiter_and_escaped_quotes() throws MapperEngineException {
    List<String> values = CsvExtractor.parseLine("a,\"b,c\",\"d\"\"e\"", ',', '"');

    assertEquals(List.of("a", "b,c", "d\"e"), values);
  }

  @Test
  void select_string_throws_when_source_is_not_csv_column_source() throws MapperEngineException {
    DynamicMapper profile = profileCsv(true, ',', '"');
    CsvExtractor extractor = CsvExtractor.fromProfile(profile);

    Map<String, String> first = extractor.selectRecords(bytes(CSV_WITH_HEADER), profile).getFirst();

    MappingRule badRule =
        new MappingRule(
            UUID.randomUUID(),
            true,
            null,
            null,
            // not a CsvColumnSource
            new XPathSource("/x"),
            null,
            OnMissing.NULL,
            null);

    MapperEngineException ex =
        assertThrows(MapperEngineException.class, () -> extractor.selectString(first, badRule));

    assertEquals("CSV_SOURCE_ERROR", ex.getCode());
  }

  private static byte[] bytes(String s) {
    return s.getBytes(StandardCharsets.UTF_8);
  }

  private static DynamicMapper profileCsv(boolean hasHeader, char delimiter, char quote) {
    CsvFormatConfig cfg = new CsvFormatConfig(delimiter, quote, hasHeader);

    return new DynamicMapper(
        UUID.randomUUID(),
        true,
        cfg,
        new RecordSelector(new CsvColumnSource(null, null), OnMissing.NULL),
        List.of());
  }

  private static MappingRule ruleWithColumn(String column, Integer index) {
    return new MappingRule(
        UUID.randomUUID(),
        true,
        null,
        null,
        new CsvColumnSource(column, index),
        null,
        OnMissing.NULL,
        null);
  }
}
