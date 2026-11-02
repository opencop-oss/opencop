// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opencop.core.api.general.business.export.ActiveFeatureExportRow;
import com.opencop.core.api.general.ports.gateway.export.ActiveFeatureExportGateway.LayerSubject;
import com.opencop.core.sdk.component.ComponentWebIT;
import com.opencop.core.sdk.component.database.AbstractDatabaseIT;
import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.testing.integration.NativeIntegrationFixture;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@ComponentWebIT(classes = ExportStorageTestApplication.class)
@TestPropertySource(
    properties = "spring.flyway.locations=filesystem:../sdk/src/main/resources/db/migration")
class JpaActiveFeatureExportGatewayTest extends AbstractDatabaseIT {

  private static final String DELETE_HELPER = "DELETE FROM geo.feature_latest_active_helper";
  private static final String DELETE_FEATURES = "DELETE FROM geo.features";
  private static final String DELETE_SERVER_LAYER_ACCESS = "DELETE FROM iam.server_layer_access";
  private static final String DELETE_LAYER = "DELETE FROM iam.layer";
  private static final String DELETE_DATA_SOURCE = "DELETE FROM profiles.data_source";
  private static final String DELETE_DATA_MAPPER = "DELETE FROM profiles.data_mapper";
  private static final String DELETE_MAPPER = "DELETE FROM profiles.mapper";
  private static final UUID DATA_MAPPER_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655445303");
  private static final UUID ARCHIVED_BY_USER_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655445304");
  private static final UUID SOURCE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655445201");
  private static final UUID OTHER_SOURCE_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655445204");
  private static final UUID LAYER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655445205");
  private static final UUID OTHER_LAYER_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655445218");
  private static final String OBJECT_ID = "object-alpha";
  private static final String OTHER_OBJECT_ID = "object-beta";
  private static final String THIRD_OBJECT_ID = "object-gamma";
  private static final String ACTIVE_PAYLOAD =
      """
      {"type":"Feature","geometry":{"type":"Point","coordinates":[1,2]},"properties":{"name":"active"}}
      """;
  private static final String STALE_PAYLOAD =
      """
      {"type":"Feature","geometry":{"type":"Point","coordinates":[3,4]},"properties":{"name":"stale"}}
      """;

  @Autowired private JdbcTemplate jdbc;
  @Autowired private JpaActiveFeatureExportGateway gateway;

  @BeforeEach
  void setUp() {
    jdbc.execute(DELETE_HELPER);
    jdbc.execute(DELETE_FEATURES);
    jdbc.execute(DELETE_SERVER_LAYER_ACCESS);
    jdbc.execute(DELETE_LAYER);
    jdbc.execute(DELETE_DATA_SOURCE);
    jdbc.execute(DELETE_DATA_MAPPER);
    jdbc.execute(DELETE_MAPPER);
    insertMapper();
  }

  @Test
  void readsOnlyRowsPointedToByLatestActiveHelperForSource() {
    var staleEventId = UUID.fromString("550e8400-e29b-41d4-a716-446655445202");
    var activeEventId = UUID.fromString("550e8400-e29b-41d4-a716-446655445203");
    insertFeature(
        SOURCE_ID,
        OBJECT_ID,
        staleEventId,
        OffsetDateTime.parse("2026-06-08T10:00:00Z"),
        OffsetDateTime.parse("2026-06-08T10:00:00Z"),
        STALE_PAYLOAD);
    insertFeature(
        SOURCE_ID,
        OBJECT_ID,
        activeEventId,
        OffsetDateTime.parse("2026-06-08T11:00:00Z"),
        OffsetDateTime.parse("2026-06-08T11:00:00Z"),
        ACTIVE_PAYLOAD);
    insertHelper(SOURCE_ID, OBJECT_ID, activeEventId, OffsetDateTime.parse("2026-06-08T11:00:00Z"));

    var rows = gateway.readSourceRows(SOURCE_ID);

    assertEquals(1, rows.size());
    assertEquals(activeEventId, rows.get(0).id());
    assertEquals(SOURCE_ID, rows.get(0).sourceId());
    assertEquals(SubjectType.SOURCE, rows.get(0).sourceType());
    assertEquals(OBJECT_ID, rows.get(0).objectId());
    assertTrue(rows.get(0).payload().contains("\"name\": \"active\""));
    assertFalse(rows.get(0).payload().contains("\"name\": \"stale\""));
  }

  @Test
  void readSourceRows_excludesRowsWhoseHelperStaleAtIsBeforeNow() {
    var expiredEventId = UUID.fromString("550e8400-e29b-41d4-a716-446655445212");
    insertFeature(
        SOURCE_ID,
        OBJECT_ID,
        expiredEventId,
        OffsetDateTime.parse("2026-06-08T11:00:00Z"),
        OffsetDateTime.parse("2026-06-08T11:00:00Z"),
        STALE_PAYLOAD);
    insertHelper(
        SOURCE_ID,
        OBJECT_ID,
        expiredEventId,
        OffsetDateTime.parse("2026-06-08T11:00:00Z"),
        OffsetDateTime.parse("2026-06-08T12:00:00Z"));

    var rows = gateway.readSourceRows(SOURCE_ID);

    assertTrue(rows.isEmpty());
  }

  @Test
  void readsRowsForSourcesAssignedToLayer() {
    var layerEventId = UUID.fromString("550e8400-e29b-41d4-a716-446655445206");
    var unassignedEventId = UUID.fromString("550e8400-e29b-41d4-a716-446655445207");
    insertLayer();
    insertDataSource(SOURCE_ID, null);
    insertDataSource(OTHER_SOURCE_ID, null);
    insertServerLayerAccess(SOURCE_ID, LAYER_ID);
    insertFeature(
        SOURCE_ID,
        OBJECT_ID,
        layerEventId,
        OffsetDateTime.parse("2026-06-08T11:00:00Z"),
        OffsetDateTime.parse("2026-06-08T11:00:00Z"),
        ACTIVE_PAYLOAD);
    insertFeature(
        OTHER_SOURCE_ID,
        OTHER_OBJECT_ID,
        unassignedEventId,
        OffsetDateTime.parse("2026-06-08T11:00:00Z"),
        OffsetDateTime.parse("2026-06-08T11:00:00Z"),
        STALE_PAYLOAD);
    insertHelper(SOURCE_ID, OBJECT_ID, layerEventId, OffsetDateTime.parse("2026-06-08T11:00:00Z"));
    insertHelper(
        OTHER_SOURCE_ID,
        OTHER_OBJECT_ID,
        unassignedEventId,
        OffsetDateTime.parse("2026-06-08T11:00:00Z"));

    var rows = gateway.readLayerRows(LAYER_ID, true);

    assertEquals(1, rows.size());
    assertEquals(layerEventId, rows.get(0).id());
    assertEquals(SOURCE_ID, rows.get(0).sourceId());
    assertEquals(SubjectType.SOURCE, rows.get(0).sourceType());
    assertEquals(OBJECT_ID, rows.get(0).objectId());
    assertTrue(rows.get(0).payload().contains("\"name\": \"active\""));
    assertFalse(rows.get(0).payload().contains("\"name\": \"stale\""));
  }

  @Test
  void readsRowsOwnedByLayerItself() {
    var layerOwnedEventId = UUID.fromString("550e8400-e29b-41d4-a716-446655445208");
    var assignedSourceEventId = UUID.fromString("550e8400-e29b-41d4-a716-446655445209");
    insertLayer();
    insertDataSource(SOURCE_ID, null);
    insertServerLayerAccess(SOURCE_ID, LAYER_ID);
    insertFeature(
        LAYER_ID,
        OBJECT_ID,
        layerOwnedEventId,
        OffsetDateTime.parse("2026-06-08T11:00:00Z"),
        OffsetDateTime.parse("2026-06-08T11:00:00Z"),
        ACTIVE_PAYLOAD);
    insertFeature(
        SOURCE_ID,
        OTHER_OBJECT_ID,
        assignedSourceEventId,
        OffsetDateTime.parse("2026-06-08T11:00:00Z"),
        OffsetDateTime.parse("2026-06-08T11:00:00Z"),
        STALE_PAYLOAD);
    insertHelper(
        LAYER_ID, OBJECT_ID, layerOwnedEventId, OffsetDateTime.parse("2026-06-08T11:00:00Z"));
    insertHelper(
        SOURCE_ID,
        OTHER_OBJECT_ID,
        assignedSourceEventId,
        OffsetDateTime.parse("2026-06-08T11:00:00Z"));

    var rows = gateway.readLayerRows(LAYER_ID, true);

    assertEquals(2, rows.size());
    assertTrue(
        rows.stream()
            .anyMatch(
                row -> row.sourceId().equals(LAYER_ID) && row.sourceType() == SubjectType.LAYER));
    assertTrue(rows.stream().anyMatch(row -> row.id().equals(layerOwnedEventId)));
    assertTrue(
        rows.stream()
            .anyMatch(
                row -> row.sourceId().equals(SOURCE_ID) && row.sourceType() == SubjectType.SOURCE));
    assertTrue(rows.stream().anyMatch(row -> row.id().equals(assignedSourceEventId)));
  }

  @Test
  void readLayerRows_whenDataSourceDataIsExcluded_returnsOnlyRowsOwnedByLayerItself() {
    var layerOwnedEventId = UUID.fromString("550e8400-e29b-41d4-a716-446655445210");
    var assignedSourceEventId = UUID.fromString("550e8400-e29b-41d4-a716-446655445211");
    insertLayer();
    insertServerLayerAccess(SOURCE_ID, LAYER_ID);
    insertFeature(
        LAYER_ID,
        OBJECT_ID,
        layerOwnedEventId,
        OffsetDateTime.parse("2026-06-08T11:00:00Z"),
        OffsetDateTime.parse("2026-06-08T11:00:00Z"),
        ACTIVE_PAYLOAD);
    insertFeature(
        SOURCE_ID,
        OTHER_OBJECT_ID,
        assignedSourceEventId,
        OffsetDateTime.parse("2026-06-08T11:00:00Z"),
        OffsetDateTime.parse("2026-06-08T11:00:00Z"),
        STALE_PAYLOAD);
    insertHelper(
        LAYER_ID, OBJECT_ID, layerOwnedEventId, OffsetDateTime.parse("2026-06-08T11:00:00Z"));
    insertHelper(
        SOURCE_ID,
        OTHER_OBJECT_ID,
        assignedSourceEventId,
        OffsetDateTime.parse("2026-06-08T11:00:00Z"));

    var rows = gateway.readLayerRows(LAYER_ID, false);

    assertEquals(1, rows.size());
    assertEquals(LAYER_ID, rows.get(0).sourceId());
    assertEquals(SubjectType.LAYER, rows.get(0).sourceType());
    assertEquals(layerOwnedEventId, rows.get(0).id());
  }

  @Test
  void readLayerRows_excludesLayerOwnedRowsWhoseHelperStaleAtIsBeforeNow() {
    var expiredLayerEventId = UUID.fromString("550e8400-e29b-41d4-a716-446655445213");
    var activeLayerEventId = UUID.fromString("550e8400-e29b-41d4-a716-446655445214");
    insertLayer();
    insertFeature(
        LAYER_ID,
        OBJECT_ID,
        expiredLayerEventId,
        OffsetDateTime.parse("2026-06-08T11:00:00Z"),
        OffsetDateTime.parse("2026-06-08T11:00:00Z"),
        STALE_PAYLOAD);
    insertFeature(
        LAYER_ID,
        OTHER_OBJECT_ID,
        activeLayerEventId,
        OffsetDateTime.parse("2026-06-08T11:00:00Z"),
        OffsetDateTime.parse("2026-06-08T11:00:00Z"),
        ACTIVE_PAYLOAD);
    insertHelper(
        LAYER_ID,
        OBJECT_ID,
        expiredLayerEventId,
        OffsetDateTime.parse("2026-06-08T11:00:00Z"),
        OffsetDateTime.parse("2026-06-08T12:00:00Z"));
    insertHelper(
        LAYER_ID,
        OTHER_OBJECT_ID,
        activeLayerEventId,
        OffsetDateTime.parse("2026-06-08T11:00:00Z"));

    var rows = gateway.readLayerRows(LAYER_ID, false);

    assertEquals(1, rows.size());
    assertEquals(activeLayerEventId, rows.get(0).id());
  }

  @Test
  void readLayerRows_whenLinkedSourceIsArchived_excludesArchivedSourceRowsButKeepsLayerRows() {
    var layerOwnedEventId = UUID.fromString("550e8400-e29b-41d4-a716-446655445215");
    var archivedSourceEventId = UUID.fromString("550e8400-e29b-41d4-a716-446655445216");
    insertLayer();
    insertDataSource(SOURCE_ID, OffsetDateTime.parse("2026-06-08T10:30:00Z"));
    insertServerLayerAccess(SOURCE_ID, LAYER_ID);
    insertFeature(
        LAYER_ID,
        OBJECT_ID,
        layerOwnedEventId,
        OffsetDateTime.parse("2026-06-08T11:00:00Z"),
        OffsetDateTime.parse("2026-06-08T11:00:00Z"),
        ACTIVE_PAYLOAD);
    insertFeature(
        SOURCE_ID,
        OTHER_OBJECT_ID,
        archivedSourceEventId,
        OffsetDateTime.parse("2026-06-08T11:00:00Z"),
        OffsetDateTime.parse("2026-06-08T11:00:00Z"),
        ACTIVE_PAYLOAD);
    insertHelper(
        LAYER_ID, OBJECT_ID, layerOwnedEventId, OffsetDateTime.parse("2026-06-08T11:00:00Z"));
    insertHelper(
        SOURCE_ID,
        OTHER_OBJECT_ID,
        archivedSourceEventId,
        OffsetDateTime.parse("2026-06-08T11:00:00Z"));

    var rows = gateway.readLayerRows(LAYER_ID, true);

    assertEquals(1, rows.size());
    assertEquals(LAYER_ID, rows.get(0).sourceId());
    assertEquals(SubjectType.LAYER, rows.get(0).sourceType());
    assertEquals(layerOwnedEventId, rows.get(0).id());
  }

  @Test
  void
      readSourceRows_whenMultipleSourcesRequested_returnsRowsFromBothSourcesInDeterministicOrder() {
    var firstSourceEventId = UUID.fromString("550e8400-e29b-41d4-a716-446655445219");
    var secondSourceEventId = UUID.fromString("550e8400-e29b-41d4-a716-446655445220");
    insertFeature(
        OTHER_SOURCE_ID,
        OTHER_OBJECT_ID,
        secondSourceEventId,
        OffsetDateTime.parse("2026-06-08T11:00:00Z"),
        OffsetDateTime.parse("2026-06-08T11:00:00Z"),
        ACTIVE_PAYLOAD);
    insertFeature(
        SOURCE_ID,
        OBJECT_ID,
        firstSourceEventId,
        OffsetDateTime.parse("2026-06-08T11:00:00Z"),
        OffsetDateTime.parse("2026-06-08T11:00:00Z"),
        ACTIVE_PAYLOAD);
    insertHelper(
        OTHER_SOURCE_ID,
        OTHER_OBJECT_ID,
        secondSourceEventId,
        OffsetDateTime.parse("2026-06-08T11:00:00Z"));
    insertHelper(
        SOURCE_ID, OBJECT_ID, firstSourceEventId, OffsetDateTime.parse("2026-06-08T11:00:00Z"));

    var rows = gateway.readSourceRows(List.of(OTHER_SOURCE_ID, SOURCE_ID));

    assertEquals(List.of(firstSourceEventId, secondSourceEventId), rowIds(rows));
    assertTrue(rows.stream().allMatch(row -> row.sourceType() == SubjectType.SOURCE));
  }

  @Test
  void readLayerRows_whenMultipleLayerSubjectsRequested_returnsRowsOwnedByBothLayers() {
    var firstLayerEventId = UUID.fromString("550e8400-e29b-41d4-a716-446655445221");
    var secondLayerEventId = UUID.fromString("550e8400-e29b-41d4-a716-446655445222");
    insertLayer(LAYER_ID, "export-layer");
    insertLayer(OTHER_LAYER_ID, "other-export-layer");
    insertFeature(
        LAYER_ID,
        OBJECT_ID,
        firstLayerEventId,
        OffsetDateTime.parse("2026-06-08T11:00:00Z"),
        OffsetDateTime.parse("2026-06-08T11:00:00Z"),
        ACTIVE_PAYLOAD);
    insertFeature(
        OTHER_LAYER_ID,
        OTHER_OBJECT_ID,
        secondLayerEventId,
        OffsetDateTime.parse("2026-06-08T11:00:00Z"),
        OffsetDateTime.parse("2026-06-08T11:00:00Z"),
        ACTIVE_PAYLOAD);
    insertHelper(
        LAYER_ID, OBJECT_ID, firstLayerEventId, OffsetDateTime.parse("2026-06-08T11:00:00Z"));
    insertHelper(
        OTHER_LAYER_ID,
        OTHER_OBJECT_ID,
        secondLayerEventId,
        OffsetDateTime.parse("2026-06-08T11:00:00Z"));

    var rows =
        gateway.readLayerRows(
            List.of(new LayerSubject(OTHER_LAYER_ID, false), new LayerSubject(LAYER_ID, false)));

    assertEquals(List.of(firstLayerEventId, secondLayerEventId), rowIds(rows));
    assertTrue(rows.stream().allMatch(row -> row.sourceType() == SubjectType.LAYER));
  }

  @Test
  void readElementRows_whenLayerAndSourceIdsRequested_returnsOnlyMatchingActiveElements() {
    var layerEventId = UUID.fromString("550e8400-e29b-41d4-a716-446655445223");
    var sourceEventId = UUID.fromString("550e8400-e29b-41d4-a716-446655445224");
    var unrequestedEventId = UUID.fromString("550e8400-e29b-41d4-a716-446655445225");
    insertLayer();
    insertFeature(
        LAYER_ID,
        OBJECT_ID,
        layerEventId,
        OffsetDateTime.parse("2026-06-08T11:00:00Z"),
        OffsetDateTime.parse("2026-06-08T11:00:00Z"),
        ACTIVE_PAYLOAD);
    insertFeature(
        SOURCE_ID,
        OTHER_OBJECT_ID,
        sourceEventId,
        OffsetDateTime.parse("2026-06-08T11:00:00Z"),
        OffsetDateTime.parse("2026-06-08T11:00:00Z"),
        ACTIVE_PAYLOAD);
    insertFeature(
        OTHER_SOURCE_ID,
        THIRD_OBJECT_ID,
        unrequestedEventId,
        OffsetDateTime.parse("2026-06-08T11:00:00Z"),
        OffsetDateTime.parse("2026-06-08T11:00:00Z"),
        STALE_PAYLOAD);
    insertHelper(LAYER_ID, OBJECT_ID, layerEventId, OffsetDateTime.parse("2026-06-08T11:00:00Z"));
    insertHelper(
        SOURCE_ID, OTHER_OBJECT_ID, sourceEventId, OffsetDateTime.parse("2026-06-08T11:00:00Z"));
    insertHelper(
        OTHER_SOURCE_ID,
        THIRD_OBJECT_ID,
        unrequestedEventId,
        OffsetDateTime.parse("2026-06-08T11:00:00Z"));

    var rows = gateway.readElementRows(List.of(sourceEventId, layerEventId));

    assertEquals(List.of(sourceEventId, layerEventId), rowIds(rows));
    assertEquals(SubjectType.SOURCE, rows.get(0).sourceType());
    assertEquals(SubjectType.LAYER, rows.get(1).sourceType());
    assertFalse(rowIds(rows).contains(unrequestedEventId));
  }

  @Test
  void sourceAndLayerReadsSkipStaleHelpersButElementReadsKeepRequestedRow() {
    var expiredSourceEventId = UUID.fromString("550e8400-e29b-41d4-a716-446655445226");
    var expiredLayerEventId = UUID.fromString("550e8400-e29b-41d4-a716-446655445227");
    var expiredElementEventId = UUID.fromString("550e8400-e29b-41d4-a716-446655445228");
    insertLayer();
    insertFeature(
        SOURCE_ID,
        OBJECT_ID,
        expiredSourceEventId,
        OffsetDateTime.parse("2026-06-08T11:00:00Z"),
        OffsetDateTime.parse("2026-06-08T11:00:00Z"),
        STALE_PAYLOAD);
    insertFeature(
        LAYER_ID,
        OTHER_OBJECT_ID,
        expiredLayerEventId,
        OffsetDateTime.parse("2026-06-08T11:00:00Z"),
        OffsetDateTime.parse("2026-06-08T11:00:00Z"),
        STALE_PAYLOAD);
    insertFeature(
        OTHER_SOURCE_ID,
        THIRD_OBJECT_ID,
        expiredElementEventId,
        OffsetDateTime.parse("2026-06-08T11:00:00Z"),
        OffsetDateTime.parse("2026-06-08T11:00:00Z"),
        STALE_PAYLOAD);
    insertHelper(
        SOURCE_ID,
        OBJECT_ID,
        expiredSourceEventId,
        OffsetDateTime.parse("2026-06-08T11:00:00Z"),
        OffsetDateTime.parse("2026-06-08T12:00:00Z"));
    insertHelper(
        LAYER_ID,
        OTHER_OBJECT_ID,
        expiredLayerEventId,
        OffsetDateTime.parse("2026-06-08T11:00:00Z"),
        OffsetDateTime.parse("2026-06-08T12:00:00Z"));
    insertHelper(
        OTHER_SOURCE_ID,
        THIRD_OBJECT_ID,
        expiredElementEventId,
        OffsetDateTime.parse("2026-06-08T11:00:00Z"),
        OffsetDateTime.parse("2026-06-08T12:00:00Z"));

    assertTrue(gateway.readSourceRows(List.of(SOURCE_ID)).isEmpty());
    assertTrue(gateway.readLayerRows(List.of(new LayerSubject(LAYER_ID, false))).isEmpty());
    var elementRows = gateway.readElementRows(List.of(expiredElementEventId));
    assertEquals(List.of(expiredElementEventId), rowIds(elementRows));
  }

  @Test
  void readElementRows_whenEventIsSupersededByNewer_stillReturnsExactlyRequestedEvent() {
    var supersededEventId = UUID.fromString("550e8400-e29b-41d4-a716-446655445240");
    var currentEventId = UUID.fromString("550e8400-e29b-41d4-a716-446655445241");
    insertFeature(
        SOURCE_ID,
        OBJECT_ID,
        supersededEventId,
        OffsetDateTime.parse("2026-06-08T10:00:00Z"),
        OffsetDateTime.parse("2026-06-08T10:00:00Z"),
        STALE_PAYLOAD);
    insertFeature(
        SOURCE_ID,
        OBJECT_ID,
        currentEventId,
        OffsetDateTime.parse("2026-06-08T11:00:00Z"),
        OffsetDateTime.parse("2026-06-08T11:00:00Z"),
        ACTIVE_PAYLOAD);
    insertHelper(
        SOURCE_ID, OBJECT_ID, currentEventId, OffsetDateTime.parse("2026-06-08T11:00:00Z"));

    var rows = gateway.readElementRows(List.of(supersededEventId));

    assertEquals(List.of(supersededEventId), rowIds(rows));
    assertTrue(rows.get(0).payload().contains("\"name\": \"stale\""));
  }

  @Test
  void readElementRows_whenEventHasNoHelperRow_stillReturnsRow() {
    var orphanEventId = UUID.fromString("550e8400-e29b-41d4-a716-446655445242");
    insertFeature(
        SOURCE_ID,
        OBJECT_ID,
        orphanEventId,
        OffsetDateTime.parse("2026-06-08T11:00:00Z"),
        OffsetDateTime.parse("2026-06-08T11:00:00Z"),
        ACTIVE_PAYLOAD);

    var rows = gateway.readElementRows(List.of(orphanEventId));

    assertEquals(List.of(orphanEventId), rowIds(rows));
  }

  @Test
  void readElementRows_whenTwoIdsShareSourceAndObject_returnsBothOrderedLatestCreatedFirst() {
    var olderEventId = UUID.fromString("550e8400-e29b-41d4-a716-446655445243");
    var newerEventId = UUID.fromString("550e8400-e29b-41d4-a716-446655445244");
    insertFeature(
        SOURCE_ID,
        OBJECT_ID,
        olderEventId,
        OffsetDateTime.parse("2026-06-08T10:00:00Z"),
        OffsetDateTime.parse("2026-06-08T10:00:00Z"),
        STALE_PAYLOAD);
    insertFeature(
        SOURCE_ID,
        OBJECT_ID,
        newerEventId,
        OffsetDateTime.parse("2026-06-08T11:00:00Z"),
        OffsetDateTime.parse("2026-06-08T11:00:00Z"),
        ACTIVE_PAYLOAD);

    var rows = gateway.readElementRows(List.of(olderEventId, newerEventId));

    assertEquals(List.of(newerEventId, olderEventId), rowIds(rows));
  }

  @Test
  void readLayerRows_whenTransitiveSourceIsArchived_excludesArchivedSourceRows() {
    var activeSourceEventId = UUID.fromString("550e8400-e29b-41d4-a716-446655445229");
    var archivedSourceEventId = UUID.fromString("550e8400-e29b-41d4-a716-446655445230");
    insertLayer();
    insertDataSource(SOURCE_ID, null);
    insertDataSource(OTHER_SOURCE_ID, OffsetDateTime.parse("2026-06-08T10:30:00Z"));
    insertServerLayerAccess(SOURCE_ID, LAYER_ID);
    insertServerLayerAccess(OTHER_SOURCE_ID, LAYER_ID);
    insertFeature(
        SOURCE_ID,
        OBJECT_ID,
        activeSourceEventId,
        OffsetDateTime.parse("2026-06-08T11:00:00Z"),
        OffsetDateTime.parse("2026-06-08T11:00:00Z"),
        ACTIVE_PAYLOAD);
    insertFeature(
        OTHER_SOURCE_ID,
        OTHER_OBJECT_ID,
        archivedSourceEventId,
        OffsetDateTime.parse("2026-06-08T11:00:00Z"),
        OffsetDateTime.parse("2026-06-08T11:00:00Z"),
        STALE_PAYLOAD);
    insertHelper(
        SOURCE_ID, OBJECT_ID, activeSourceEventId, OffsetDateTime.parse("2026-06-08T11:00:00Z"));
    insertHelper(
        OTHER_SOURCE_ID,
        OTHER_OBJECT_ID,
        archivedSourceEventId,
        OffsetDateTime.parse("2026-06-08T11:00:00Z"));

    var rows = gateway.readLayerRows(List.of(new LayerSubject(LAYER_ID, true)));

    assertEquals(1, rows.size());
    assertEquals(activeSourceEventId, rows.get(0).id());
    assertEquals(SOURCE_ID, rows.get(0).sourceId());
    assertEquals(SubjectType.SOURCE, rows.get(0).sourceType());
  }

  private void insertFeature(
      UUID sourceId,
      String objectId,
      UUID eventId,
      OffsetDateTime createdAt,
      OffsetDateTime startAt,
      String payload) {
    jdbc.update(
        """
        INSERT INTO geo.features (
          id, feature_kind, source, object_id, created_at, start_at, geom, payload
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb))
        """,
        eventId,
        "event",
        sourceId,
        objectId,
        createdAt,
        startAt,
        "POINT(1 2)",
        payload);
  }

  private void insertHelper(
      UUID sourceId, String objectId, UUID eventId, OffsetDateTime playbackAt) {
    insertHelper(sourceId, objectId, eventId, playbackAt, null);
  }

  private void insertHelper(
      UUID sourceId,
      String objectId,
      UUID eventId,
      OffsetDateTime playbackAt,
      OffsetDateTime staleAt) {
    jdbc.update(
        """
        INSERT INTO geo.feature_latest_active_helper (
          source, object_id, event_id, playback_at, created_at, first_emission_at, stale_at
        )
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """,
        sourceId,
        objectId,
        eventId,
        playbackAt,
        playbackAt,
        OffsetDateTime.parse("2026-06-08T10:00:00Z"),
        staleAt);
  }

  private void insertLayer() {
    insertLayer(LAYER_ID, "export-layer");
  }

  private void insertLayer(UUID layerId, String name) {
    jdbc.update("INSERT INTO iam.layer (id, name) VALUES (?, ?)", layerId, name);
  }

  private void insertMapper() {
    jdbc.update(
        "INSERT INTO profiles.mapper (id, qualifier) VALUES (?, 'INGRESS'::profiles.mapper_qualifier) ON CONFLICT (id) DO NOTHING",
        DATA_MAPPER_ID);
    jdbc.update(
        "INSERT INTO profiles.data_mapper (id, version, name, mapper) VALUES (?, ?, ?, CAST(? AS jsonb))",
        DATA_MAPPER_ID,
        1,
        "mapper-export-test",
        "{}");
  }

  private void insertDataSource(UUID sourceId, OffsetDateTime archivedAt) {
    if (archivedAt != null) {
      insertUserIfMissing(ARCHIVED_BY_USER_ID);
    }
    NativeIntegrationFixture.seed(jdbc);
    jdbc.update(
        """
        INSERT INTO profiles.data_source (
            id, name, ingress_mapper_id, connection_spec, archived_at, archived_by,
            integration_id, connection_schema_version
        ) VALUES (?, ?, ?, CAST('{}' AS jsonb), ?, ?, ?, ?)
        """,
        new Object[] {
          sourceId,
          "source-" + sourceId,
          DATA_MAPPER_ID,
          archivedAt,
          archivedAt == null ? null : ARCHIVED_BY_USER_ID,
          NativeIntegrationFixture.CONNECTION_SCHEMA.integrationId(),
          NativeIntegrationFixture.CONNECTION_SCHEMA.schemaVersion()
        },
        new int[] {
          Types.OTHER,
          Types.VARCHAR,
          Types.OTHER,
          Types.TIMESTAMP_WITH_TIMEZONE,
          Types.OTHER,
          Types.OTHER,
          Types.INTEGER
        });
  }

  private void insertUserIfMissing(UUID userId) {
    jdbc.update(
        """
        INSERT INTO opencop_user (
          id, idp_user_id, idp_username, idp_issuer, username, email, first_name, last_name, display_name
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (id) DO NOTHING
        """,
        userId,
        "idp-" + userId,
        "user-" + userId,
        "https://issuer.test",
        "user-" + userId,
        "user@example.test",
        "Test",
        "User",
        "Test User");
  }

  private void insertServerLayerAccess(UUID sourceId, UUID layerId) {
    jdbc.update(
        """
        INSERT INTO iam.server_layer_access (server_id, layer_id, role)
        VALUES (?, ?, CAST(? AS iam.server_role))
        """,
        sourceId,
        layerId,
        "VIEWER");
  }

  private static List<UUID> rowIds(List<ActiveFeatureExportRow> rows) {
    return rows.stream().map(ActiveFeatureExportRow::id).toList();
  }
}
