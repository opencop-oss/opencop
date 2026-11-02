// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.export;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.opencop.core.sdk.model.SubjectType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class CsvFeatureExportMapperTest {

  private static final UUID ROW_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655445101");
  private static final UUID SOURCE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655445102");
  private static final String OBJECT_ID = "object-alpha";
  private static final String HEADER =
      "id,sourceId,sourceType,objectId,displayName,time,start,stale,authorFirstName,authorLastName,authorUsername,elementType,symbol,fillColor,strokeColor,strokeStyle,strokeWeight,radius,geometry,attributes,href\n";

  private final CsvFeatureExportMapper mapper = new CsvFeatureExportMapper();

  @Test
  void exposesCsvFormatMetadata() {
    assertEquals(".csv", mapper.fileExtension());
    assertEquals(new MediaType("text", "csv"), mapper.contentType());
  }

  @Test
  void writesRequiredColumnsFromFeaturePayload() {
    var payload =
        """
        {
          "type": "Feature",
          "geometry": {"type": "Point", "coordinates": [45.5, -73.6]},
          "properties": {
            "name": "Alpha",
            "attributes": {"status": "ready", "priority": 7}
          }
        }
        """;
    var row = row(OBJECT_ID, payload);

    var csv = mapper.map(List.of(row));

    assertEquals(
        HEADER
            + ROW_ID
            + ","
            + SOURCE_ID
            + ",SOURCE"
            + ",object-alpha,Alpha"
            + ",".repeat(14)
            + "\"{\"\"type\"\":\"\"Point\"\",\"\"coordinates\"\":[45.5,-73.6]}\","
            + "\"{\"\"status\"\":\"\"ready\"\",\"\"priority\"\":7}\",\n",
        new String(csv));
  }

  @Test
  void writesDisplayNameFromOpenCopPropertiesWhenNameIsMissing() {
    var payload =
        """
        {
          "type": "Feature",
          "geometry": {"type": "Point", "coordinates": [1, 2]},
          "properties": {
            "opencop": {
              "displayName": "Layer feature",
              "attributes": {"ignored": true}
            }
          }
        }
        """;
    var row = row(OBJECT_ID, payload);

    var csv = mapper.map(List.of(row));

    assertEquals(
        HEADER
            + ROW_ID
            + ","
            + SOURCE_ID
            + ",SOURCE"
            + ",object-alpha,Layer feature"
            + ",".repeat(14)
            + "\"{\"\"type\"\":\"\"Point\"\",\"\"coordinates\"\":[1,2]}\",,\n",
        new String(csv));
  }

  @Test
  void writesOpenCopTimestampsAuthorAndElementTypeToSeparateColumns() {
    var payload =
        """
        {
          "type": "Feature",
          "geometry": {"type": "Point", "coordinates": [1, 2]},
          "properties": {
            "opencop": {
              "displayName": "Layer feature",
              "timestampz": {
                "time": 1781034466065,
                "start": 1781034465939,
                "stale": 32503723200000
              },
              "author": {
                "firstName": "Power",
                "lastName": "User",
                "username": "admin_username"
              },
              "element": {
                "type": "symbol",
                "symbol": "10011000001207000000",
                "fillColor": "#ff000033",
                "strokeColor": "#ff0000b3",
                "strokeStyle": "solid",
                "strokeWeight": 3.0,
                "radius": 128604.65616288423
              }
            }
          }
        }
        """;
    var row = row(OBJECT_ID, payload);

    var csv = mapper.map(List.of(row));

    assertEquals(
        HEADER
            + ROW_ID
            + ","
            + SOURCE_ID
            + ",SOURCE"
            + ",object-alpha,Layer feature,1781034466065,1781034465939,32503723200000,Power,User,admin_username,symbol,10011000001207000000,#ff000033,#ff0000b3,solid,3.0,128604.65616288423,"
            + "\"{\"\"type\"\":\"\"Point\"\",\"\"coordinates\"\":[1,2]}\",,\n",
        new String(csv));
  }

  @Test
  void writesAMediaIconsHrefToTheLastColumn() {
    var payload =
        """
        {
          "type": "Feature",
          "properties": {
            "opencop": {
              "displayName": "Icon feature",
              "element": {
                "type": "media-icon",
                "href": "https://example.com/icon.png"
              }
            }
          }
        }
        """;
    var row = row(OBJECT_ID, payload);

    var csv = mapper.map(List.of(row));

    assertEquals(
        HEADER
            + ROW_ID
            + ","
            + SOURCE_ID
            + ",SOURCE"
            + ",object-alpha,Icon feature"
            + ",".repeat(7)
            + "media-icon"
            + ",".repeat(9)
            + "https://example.com/icon.png\n",
        new String(csv));
  }

  @Test
  void writesNameBeforeOpenCopDisplayNameWhenBothArePresent() {
    var payload =
        """
        {
          "type": "Feature",
          "properties": {
            "name": "Top level name",
            "opencop": {"displayName": "OpenCOP display name"},
            "attributes": {}
          }
        }
        """;
    var row = row(OBJECT_ID, payload);

    var csv = mapper.map(List.of(row));

    assertEquals(
        HEADER
            + ROW_ID
            + ","
            + SOURCE_ID
            + ",SOURCE"
            + ",object-alpha,Top level name"
            + ",".repeat(15)
            + "{},\n",
        new String(csv));
  }

  @Test
  void prefixesFormulaLeadingDisplayNameWithApostrophe() {
    var payload =
        """
        {
          "type": "Feature",
          "geometry": {"type": "Point", "coordinates": [1, 2]},
          "properties": {
            "name": "=SUM(1,2)",
            "attributes": {"status": "ready"}
          }
        }
        """;
    var row = row(OBJECT_ID, payload);

    var csv = mapper.map(List.of(row));

    assertEquals(
        HEADER
            + ROW_ID
            + ","
            + SOURCE_ID
            + ",SOURCE"
            + ",object-alpha,"
            + "\"'=SUM(1,2)\","
            + ",".repeat(13)
            + "\"{\"\"type\"\":\"\"Point\"\",\"\"coordinates\"\":[1,2]}\","
            + "\"{\"\"status\"\":\"\"ready\"\"}\",\n",
        new String(csv));
  }

  @Test
  void prefixesFormulaLeadingObjectIdWithApostrophe() {
    var payload =
        """
        {
          "type": "Feature",
          "properties": {
            "name": "Alpha",
            "attributes": {}
          }
        }
        """;
    var row = row("=object-alpha", payload);

    var csv = mapper.map(List.of(row));

    assertEquals(
        HEADER
            + ROW_ID
            + ","
            + SOURCE_ID
            + ",SOURCE,'=object-alpha,Alpha"
            + ",".repeat(15)
            + "{},\n",
        new String(csv));
  }

  @Test
  void writesBlankCellsWhenPayloadOmitsDisplayNameGeometryAndAttributes() {
    var payload =
        """
        {
          "type": "Feature",
          "properties": {}
        }
        """;
    var row = row(OBJECT_ID, payload);

    var csv = mapper.map(List.of(row));

    assertEquals(
        HEADER + ROW_ID + "," + SOURCE_ID + ",SOURCE,object-alpha" + ",".repeat(16) + ",\n",
        new String(csv));
  }

  @Test
  void prefixesOtherFormulaLeadingDisplayNamesWithApostrophe() {
    var payload =
        """
        {
          "type": "Feature",
          "properties": {
            "name": "+cmd",
            "attributes": {}
          }
        }
        """;
    var row = row(OBJECT_ID, payload);

    var csv = mapper.map(List.of(row));

    assertEquals(
        HEADER + ROW_ID + "," + SOURCE_ID + ",SOURCE,object-alpha,'+cmd" + ",".repeat(15) + "{},\n",
        new String(csv));
  }

  @Test
  void prefixesTabLeadingFormulaWithApostrophe() {
    var payload =
        "{\"type\":\"Feature\",\"properties\":{\"name\":\"\\t=SUM(1,2)\",\"attributes\":{}}}";
    var row = row(OBJECT_ID, payload);

    var csv = mapper.map(List.of(row));

    assertEquals(
        HEADER
            + ROW_ID
            + ","
            + SOURCE_ID
            + ",SOURCE,object-alpha,\"'\t=SUM(1,2)\""
            + ",".repeat(15)
            + "{},\n",
        new String(csv));
  }

  @Test
  void prefixesCarriageReturnLeadingFormulaWithApostropheAndQuotesCell() {
    var payload =
        "{\"type\":\"Feature\",\"properties\":{\"name\":\"\\r=SUM(1,2)\",\"attributes\":{}}}";
    var row = row(OBJECT_ID, payload);

    var csv = mapper.map(List.of(row));

    assertEquals(
        HEADER
            + ROW_ID
            + ","
            + SOURCE_ID
            + ",SOURCE,object-alpha,\"'\r=SUM(1,2)\""
            + ",".repeat(15)
            + "{},\n",
        new String(csv));
  }

  @Test
  void prefixesSpaceLeadingFormulaWithApostrophe() {
    var payload =
        "{\"type\":\"Feature\",\"properties\":{\"name\":\" =SUM(1,2)\",\"attributes\":{}}}";
    var row = row(OBJECT_ID, payload);

    var csv = mapper.map(List.of(row));

    assertEquals(
        HEADER
            + ROW_ID
            + ","
            + SOURCE_ID
            + ",SOURCE,object-alpha,\"' =SUM(1,2)\""
            + ",".repeat(15)
            + "{},\n",
        new String(csv));
  }

  @Test
  void prefixesMinusLeadingFormulaWithApostrophe() {
    var payload = "{\"type\":\"Feature\",\"properties\":{\"name\":\"-1-2\",\"attributes\":{}}}";
    var row = row(OBJECT_ID, payload);

    var csv = mapper.map(List.of(row));

    assertEquals(
        HEADER + ROW_ID + "," + SOURCE_ID + ",SOURCE,object-alpha,'-1-2" + ",".repeat(15) + "{},\n",
        new String(csv));
  }

  @Test
  void prefixesFormulaLeadingOpenCopAuthorFirstNameWithApostrophe() {
    var payload =
        "{\"type\":\"Feature\",\"properties\":{\"name\":\"Alpha\",\"opencop\":"
            + "{\"author\":{\"firstName\":\"@evil\"}},\"attributes\":{}}}";
    var row = row(OBJECT_ID, payload);

    var csv = mapper.map(List.of(row));

    assertEquals(
        HEADER
            + ROW_ID
            + ","
            + SOURCE_ID
            + ",SOURCE,object-alpha,Alpha,,,,'@evil"
            + ",".repeat(11)
            + "{},\n",
        new String(csv));
  }

  @Test
  void quotesCellContainingBareCarriageReturn() {
    var payload =
        "{\"type\":\"Feature\",\"properties\":{\"name\":\"line1\\rline2\",\"attributes\":{}}}";
    var row = row(OBJECT_ID, payload);

    var csv = mapper.map(List.of(row));

    assertEquals(
        HEADER
            + ROW_ID
            + ","
            + SOURCE_ID
            + ",SOURCE,object-alpha,\"line1\rline2\""
            + ",".repeat(15)
            + "{},\n",
        new String(csv));
  }

  private static ActiveFeatureExportRow row(String objectId, String payload) {
    return new ActiveFeatureExportRow(ROW_ID, SOURCE_ID, SubjectType.SOURCE, objectId, payload);
  }
}
