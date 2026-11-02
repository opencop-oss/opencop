// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.export;

import com.opencop.core.api.general.technical.model.export.ExportRequest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class CsvFeatureExportMapper implements FeatureExportMapper {

  private static final String HEADER =
      "id,sourceId,sourceType,objectId,displayName,time,start,stale,authorFirstName,authorLastName,authorUsername,elementType,symbol,fillColor,strokeColor,strokeStyle,strokeWeight,radius,geometry,attributes,href\n";
  private static final String GEOMETRY_FIELD = "geometry";
  private static final String PROPERTIES_FIELD = "properties";
  private static final String OPENCOP_FIELD = "opencop";
  private static final String NAME_FIELD = "name";
  private static final String DISPLAY_NAME_FIELD = "displayName";
  private static final String ATTRIBUTES_FIELD = "attributes";
  private static final String TIMESTAMPZ_FIELD = "timestampz";
  private static final String TIME_FIELD = "time";
  private static final String START_FIELD = "start";
  private static final String STALE_FIELD = "stale";
  private static final String AUTHOR_FIELD = "author";
  private static final String FIRST_NAME_FIELD = "firstName";
  private static final String LAST_NAME_FIELD = "lastName";
  private static final String USERNAME_FIELD = "username";
  private static final String ELEMENT_FIELD = "element";
  private static final String TYPE_FIELD = "type";
  private static final String SYMBOL_FIELD = "symbol";
  private static final String FILL_COLOR_FIELD = "fillColor";
  private static final String STROKE_COLOR_FIELD = "strokeColor";
  private static final String STROKE_STYLE_FIELD = "strokeStyle";
  private static final String STROKE_WEIGHT_FIELD = "strokeWeight";
  private static final String RADIUS_FIELD = "radius";
  private static final String HREF_FIELD = "href";
  private static final String EMPTY_CELL = "";
  private static final char CSV_QUOTE = '"';
  private static final String CSV_QUOTE_TEXT = "\"";
  private static final String ESCAPED_CSV_QUOTE = "\"\"";
  private static final char CSV_SEPARATOR = ',';
  private static final char NEWLINE = '\n';
  private static final char CARRIAGE_RETURN = '\r';
  private static final List<Character> FORMULA_PREFIXES = List.of('=', '+', '-', '@');
  private static final String FILE_EXTENSION = ".csv";
  private static final MediaType CONTENT_TYPE = new MediaType("text", "csv");

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public byte[] map(List<ActiveFeatureExportRow> rows) {
    var csv = new StringBuilder(HEADER);
    rows.forEach(row -> appendRow(csv, row));
    return csv.toString().getBytes(StandardCharsets.UTF_8);
  }

  @Override
  public ExportRequest.Format format() {
    return ExportRequest.Format.CSV;
  }

  @Override
  public String fileExtension() {
    return FILE_EXTENSION;
  }

  @Override
  public MediaType contentType() {
    return CONTENT_TYPE;
  }

  private void appendRow(StringBuilder csv, ActiveFeatureExportRow row) {
    var payload = readPayload(row.payload());
    appendCell(csv, row.id().toString());
    appendCell(csv, row.sourceId().toString());
    appendCell(csv, row.sourceType().name());
    appendCell(csv, safeText(row.objectId()));
    appendCell(csv, safeText(displayName(payload)));
    appendOpenCopTextCell(csv, payload, TIMESTAMPZ_FIELD, TIME_FIELD);
    appendOpenCopTextCell(csv, payload, TIMESTAMPZ_FIELD, START_FIELD);
    appendOpenCopTextCell(csv, payload, TIMESTAMPZ_FIELD, STALE_FIELD);
    appendOpenCopTextCell(csv, payload, AUTHOR_FIELD, FIRST_NAME_FIELD);
    appendOpenCopTextCell(csv, payload, AUTHOR_FIELD, LAST_NAME_FIELD);
    appendOpenCopTextCell(csv, payload, AUTHOR_FIELD, USERNAME_FIELD);
    appendOpenCopTextCell(csv, payload, ELEMENT_FIELD, TYPE_FIELD);
    appendOpenCopTextCell(csv, payload, ELEMENT_FIELD, SYMBOL_FIELD);
    appendOpenCopTextCell(csv, payload, ELEMENT_FIELD, FILL_COLOR_FIELD);
    appendOpenCopTextCell(csv, payload, ELEMENT_FIELD, STROKE_COLOR_FIELD);
    appendOpenCopTextCell(csv, payload, ELEMENT_FIELD, STROKE_STYLE_FIELD);
    appendOpenCopTextCell(csv, payload, ELEMENT_FIELD, STROKE_WEIGHT_FIELD);
    appendOpenCopTextCell(csv, payload, ELEMENT_FIELD, RADIUS_FIELD);
    appendCell(csv, jsonOrEmpty(payload.path(GEOMETRY_FIELD)));
    appendCell(csv, jsonOrEmpty(properties(payload).path(ATTRIBUTES_FIELD)));
    // Last, so every column before it keeps the position CSV consumers already read it from.
    appendLastCell(
        csv, safeText(opencop(payload).path(ELEMENT_FIELD).path(HREF_FIELD).asString(EMPTY_CELL)));
  }

  private JsonNode readPayload(String payload) {
    try {
      return objectMapper.readTree(payload);
    } catch (JacksonException e) {
      throw new IllegalArgumentException("Feature export payload must be valid JSON", e);
    }
  }

  private JsonNode properties(JsonNode payload) {
    return payload.path(PROPERTIES_FIELD);
  }

  private String displayName(JsonNode payload) {
    var properties = properties(payload);
    var name = properties.path(NAME_FIELD).asString(EMPTY_CELL);
    if (!name.isEmpty()) {
      return name;
    }
    return properties.path(OPENCOP_FIELD).path(DISPLAY_NAME_FIELD).asString(EMPTY_CELL);
  }

  private JsonNode opencop(JsonNode payload) {
    return properties(payload).path(OPENCOP_FIELD);
  }

  private void appendOpenCopTextCell(
      StringBuilder csv, JsonNode payload, String parent, String child) {
    appendCell(csv, safeText(opencop(payload).path(parent).path(child).asString(EMPTY_CELL)));
  }

  private String jsonOrEmpty(JsonNode node) {
    if (node.isMissingNode() || node.isNull()) {
      return EMPTY_CELL;
    }
    try {
      return objectMapper.writeValueAsString(node);
    } catch (JacksonException e) {
      throw new IllegalArgumentException("Feature export payload contains invalid JSON", e);
    }
  }

  private String safeText(String value) {
    int firstMeaningful = 0;
    while (firstMeaningful < value.length()
        && isSpreadsheetLeadingNoise(value.charAt(firstMeaningful))) {
      firstMeaningful++;
    }
    if (firstMeaningful == value.length()
        || !FORMULA_PREFIXES.contains(value.charAt(firstMeaningful))) {
      return value;
    }
    return "'" + value;
  }

  private boolean isSpreadsheetLeadingNoise(char c) {
    return c == ' ' || c == '\t' || c == '\r' || c == '\n';
  }

  private void appendCell(StringBuilder csv, String value) {
    csv.append(escape(value)).append(CSV_SEPARATOR);
  }

  private void appendLastCell(StringBuilder csv, String value) {
    csv.append(escape(value)).append(NEWLINE);
  }

  private String escape(String value) {
    var needsQuoting =
        value.indexOf(CSV_SEPARATOR) >= 0
            || value.indexOf(CSV_QUOTE) >= 0
            || value.indexOf(NEWLINE) >= 0
            || value.indexOf(CARRIAGE_RETURN) >= 0;
    if (!needsQuoting) {
      return value;
    }
    return CSV_QUOTE + value.replace(CSV_QUOTE_TEXT, ESCAPED_CSV_QUOTE) + CSV_QUOTE;
  }
}
