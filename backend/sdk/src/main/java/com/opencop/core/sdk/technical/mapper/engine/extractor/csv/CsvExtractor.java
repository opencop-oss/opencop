// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.mapper.engine.extractor.csv;

import com.opencop.core.sdk.model.mapper.config.CsvFormatConfig;
import com.opencop.core.sdk.model.mapper.profile.DynamicMapper;
import com.opencop.core.sdk.model.mapper.rules.MappingRule;
import com.opencop.core.sdk.model.mapper.source.CsvColumnSource;
import com.opencop.core.sdk.technical.mapper.engine.MapperEngineException;
import com.opencop.core.sdk.technical.mapper.engine.extractor.RecordExtractor;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CsvExtractor implements RecordExtractor<Map<String, String>> {

  // TODO: this needs to be configurable; do not use a value lower than
  // opencop.layer.import.max-bytes.
  private static final long MAX_PAYLOAD_BYTES = 100L * 1_024 * 1_024;
  private static final int MAX_LINE_LENGTH = 10_000_000;
  private static final int MAX_ROWS = 100_000;
  private static final int MAX_COLUMNS = 10_000;

  private final CsvFormatConfig cfg;
  private final ConcurrentHashMap<String, Map<String, Integer>> headerCache =
      new ConcurrentHashMap<>();

  private CsvExtractor(CsvFormatConfig cfg) {
    this.cfg = cfg;
  }

  public static CsvExtractor fromProfile(DynamicMapper profile) {
    CsvFormatConfig cfg =
        profile.formatConfig() instanceof CsvFormatConfig c ? c : CsvFormatConfig.defaults();
    return new CsvExtractor(cfg);
  }

  @Override
  public byte[] toBytes(Map<String, String> recordEntry) {
    // For debugging/error payloads, a stable JSON-ish view is usually fine
    // (avoid pulling ObjectMapper into this class)
    try {
      return recordEntry.toString().getBytes(StandardCharsets.UTF_8);
    } catch (Exception _) {
      return null;
    }
  }

  @Override
  public List<Map<String, String>> selectRecords(byte[] payload, DynamicMapper profile)
      throws MapperEngineException {
    if (payload.length > MAX_PAYLOAD_BYTES) {
      throw new MapperEngineException(
          "CSV_CONSTRAINTS_ERROR", "CSV payload exceeds maximum size", null);
    }

    try (var br =
        new BufferedReader(new InputStreamReader(new ByteArrayInputStream(payload), charset()))) {
      List<String> headers;
      Map<String, Integer> headerIndex = Map.of();
      List<Map<String, String>> out = new ArrayList<>();
      int rowCount = 0;

      String line = br.readLine();
      if (line == null) {
        return List.of();
      }

      if (line.length() > MAX_LINE_LENGTH) {
        throw new MapperEngineException(
            "CSV_CONSTRAINTS_ERROR", "CSV line exceeds maximum length", null);
      }

      if (cfg.hasHeader()) {
        headers = parseLine(line, cfg.delimiter(), cfg.quote());
        headerIndex = headerCache.computeIfAbsent(line, _ -> index(headers));
        line = br.readLine();
      }

      while (line != null) {
        if (line.length() > MAX_LINE_LENGTH) {
          throw new MapperEngineException(
              "CSV_CONSTRAINTS_ERROR", "CSV line exceeds maximum length", null);
        }
        if (!line.isBlank()) {
          if (rowCount >= MAX_ROWS) {
            throw new MapperEngineException(
                "CSV_CONSTRAINTS_ERROR", "CSV row count exceeds maximum", null);
          }

          List<String> values = parseLine(line, cfg.delimiter(), cfg.quote());

          Map<String, String> row;
          if (cfg.hasHeader()) {
            row = LinkedHashMap.newLinkedHashMap(headerIndex.size());
            for (var e : headerIndex.entrySet()) {
              int idx = e.getValue();
              row.put(e.getKey(), idx < values.size() ? values.get(idx) : null);
            }
          } else {
            // No header: use numeric keys "0","1","2"... so CsvColumnSource can use index
            row = LinkedHashMap.newLinkedHashMap(values.size());
            for (int c = 0; c < values.size(); c++) {
              row.put(String.valueOf(c), values.get(c));
            }
          }

          out.add(row);
          rowCount++;
        }

        line = br.readLine();
      }

      return out;
    } catch (Exception e) {
      throw new MapperEngineException("CSV_PARSING_ERROR", "Failed to parse CSV payload", e);
    }
  }

  @Override
  public String selectString(Map<String, String> context, MappingRule rule)
      throws MapperEngineException {
    if (!(rule.source() instanceof CsvColumnSource src)) {
      throw new MapperEngineException(
          "CSV_SOURCE_ERROR", "Rule source is not CsvColumnSource", null);
    }
    // Prefer column name
    if (src.column() != null && !src.column().isBlank()) {
      return context.get(src.column());
    }

    if (src.index() != null) {
      return context.get(String.valueOf(src.index()));
    }

    throw new MapperEngineException(
        "CSV_SOURCE_ERROR", "CsvColumnSource requires column or index", null);
  }

  // ----- helpers -----

  private static Charset charset() {
    return StandardCharsets.UTF_8;
  }

  private static Map<String, Integer> index(List<String> headers) {
    Map<String, Integer> map = new LinkedHashMap<>();
    for (int i = 0; i < headers.size(); i++) {
      map.put(headers.get(i), i);
    }
    return Collections.unmodifiableMap(map);
  }

  /**
   * Minimal CSV line parser: handles delimiter + quote, no multiline fields. Good enough to start;
   * if you need RFC-complete CSV later, swap to commons-csv.
   */
  static List<String> parseLine(String line, char delimiter, char quote)
      throws MapperEngineException {
    List<String> out = new ArrayList<>();
    StringBuilder cur = new StringBuilder();
    boolean inQuotes = false;

    for (int i = 0; i < line.length(); i++) {
      char ch = line.charAt(i);

      if (ch == quote) {
        // double-quote escaping: "" inside quoted field
        if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == quote) {
          cur.append(quote);
          i++;
        } else {
          inQuotes = !inQuotes;
        }
        continue;
      }

      if (ch == delimiter && !inQuotes) {
        out.add(cur.toString());
        if (out.size() > MAX_COLUMNS) {
          throw new MapperEngineException(
              "CSV_CONSTRAINTS_ERROR", "CSV column count exceeds maximum", null);
        }
        cur.setLength(0);
        continue;
      }

      cur.append(ch);
    }

    out.add(cur.toString());
    if (out.size() > MAX_COLUMNS) {
      throw new MapperEngineException(
          "CSV_CONSTRAINTS_ERROR", "CSV column count exceeds maximum", null);
    }
    return out;
  }
}
