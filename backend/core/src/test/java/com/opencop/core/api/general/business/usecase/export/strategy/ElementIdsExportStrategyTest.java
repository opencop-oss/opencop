// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.export.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.business.export.ActiveFeatureExportRow;
import com.opencop.core.api.general.business.export.ExportFilenameFactory;
import com.opencop.core.api.general.business.export.ExportSelection;
import com.opencop.core.api.general.business.policy.ExportAccessPolicy;
import com.opencop.core.api.general.ports.gateway.export.ActiveFeatureExportGateway;
import com.opencop.core.api.general.technical.model.export.ExportRequest;
import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ElementIdsExportStrategyTest {

  private static final UUID ELEMENT_ONE = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
  private static final UUID ELEMENT_TWO = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
  private static final UUID LAYER_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
  private static final UUID SOURCE_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
  private static final UUID EXPORT_ID = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
  private static final OffsetDateTime GENERATED_AT =
      OffsetDateTime.of(2026, 6, 8, 12, 0, 0, 0, ZoneOffset.UTC);

  private ExportAccessPolicy accessPolicy;
  private ActiveFeatureExportGateway gateway;
  private ExportFilenameFactory filenameFactory;
  private ElementIdsExportStrategy strategy;

  @BeforeEach
  void setUp() {
    accessPolicy = mock(ExportAccessPolicy.class);
    gateway = mock(ActiveFeatureExportGateway.class);
    filenameFactory = mock(ExportFilenameFactory.class);
    strategy = new ElementIdsExportStrategy(accessPolicy, gateway, filenameFactory);
  }

  @Test
  void supportedMode_isElementIds() {
    assertEquals(ExportSelection.Mode.ELEMENT_IDS, strategy.supportedMode());
  }

  @Test
  void assertActive_isNoOp() {
    strategy.assertActive(elementSelection(ELEMENT_ONE));

    verifyNoInteractions(gateway, accessPolicy, filenameFactory);
  }

  @Test
  void assertAuthorizedPreFetch_isNoOp() {
    strategy.assertAuthorizedPreFetch(mock(OpenCopPrincipal.class), elementSelection(ELEMENT_ONE));

    verifyNoInteractions(accessPolicy);
  }

  @Test
  void readRows_callsElementGateway() {
    var rows = List.of(row(ELEMENT_ONE, LAYER_ID, SubjectType.LAYER));
    when(gateway.readElementRows(List.of(ELEMENT_ONE, ELEMENT_TWO))).thenReturn(rows);

    var result = strategy.readRows(elementSelection(ELEMENT_ONE, ELEMENT_TWO));

    assertEquals(rows, result);
  }

  @Test
  void assertRowsIntegrity_whenRowCountMatches_passes() {
    strategy.assertRowsIntegrity(
        elementSelection(ELEMENT_ONE, ELEMENT_TWO),
        List.of(
            row(ELEMENT_ONE, LAYER_ID, SubjectType.LAYER),
            row(ELEMENT_TWO, SOURCE_ID, SubjectType.SOURCE)));
  }

  @Test
  void assertRowsIntegrity_whenRowCountSmaller_throwsNotFound() {
    var selection = elementSelection(ELEMENT_ONE, ELEMENT_TWO);

    var exception =
        assertThrows(
            GlobalOpenCopException.class,
            () ->
                strategy.assertRowsIntegrity(
                    selection, List.of(row(ELEMENT_ONE, LAYER_ID, SubjectType.LAYER))));

    assertEquals(HttpStatus.NOT_FOUND, exception.getHttpStatus());
    assertEquals("Export elements not found", exception.getMessage());
  }

  @Test
  void assertAuthorizedPostFetch_authorizesBothTypesFromRows() {
    var principal = mock(OpenCopPrincipal.class);
    var rows =
        List.of(
            row(ELEMENT_ONE, LAYER_ID, SubjectType.LAYER),
            row(ELEMENT_TWO, SOURCE_ID, SubjectType.SOURCE));

    strategy.assertAuthorizedPostFetch(principal, elementSelection(ELEMENT_ONE, ELEMENT_TWO), rows);

    verify(accessPolicy).assertCanExportLayers(principal, List.of(LAYER_ID));
    verify(accessPolicy).assertCanExportSources(principal, List.of(SOURCE_ID));
  }

  @Test
  void assertAuthorizedPostFetch_dedupesSourceIds() {
    var principal = mock(OpenCopPrincipal.class);
    var rows =
        List.of(
            row(ELEMENT_ONE, SOURCE_ID, SubjectType.SOURCE),
            row(ELEMENT_TWO, SOURCE_ID, SubjectType.SOURCE));

    strategy.assertAuthorizedPostFetch(principal, elementSelection(ELEMENT_ONE, ELEMENT_TWO), rows);

    verify(accessPolicy).assertCanExportSources(principal, List.of(SOURCE_ID));
    verify(accessPolicy, never()).assertCanExportLayers(any(), any());
  }

  @Test
  void assertAuthorizedPostFetch_withOnlyLayerRows_skipsSourcePolicy() {
    var principal = mock(OpenCopPrincipal.class);
    var rows = List.of(row(ELEMENT_ONE, LAYER_ID, SubjectType.LAYER));

    strategy.assertAuthorizedPostFetch(principal, elementSelection(ELEMENT_ONE), rows);

    verify(accessPolicy).assertCanExportLayers(principal, List.of(LAYER_ID));
    verify(accessPolicy, never()).assertCanExportSources(any(), any());
  }

  /**
   * Contract: when two element IDs in the same request resolve to the same (sourceId, objectId),
   * the strategy keeps only the first row returned by the gateway. Its latest-first ordering makes
   * that row the latest event. Older rows are silently omitted from the export and the response
   * does not report which IDs were dropped.
   */
  @Test
  void finalizeRows_silentlyOmitsOlderRequestedIdWhenItSharesSourceAndObjectWithLatestRow() {
    var latestId = UUID.fromString("99999999-9999-9999-9999-999999999991");
    var olderRequestedId = UUID.fromString("99999999-9999-9999-9999-999999999992");
    var distinctId = UUID.fromString("99999999-9999-9999-9999-999999999993");
    // Gateway returns rows pre-sorted so the latest event for a given (source, object) is first.
    var raw =
        List.of(
            new ActiveFeatureExportRow(latestId, SOURCE_ID, SubjectType.SOURCE, "object-1", "{}"),
            new ActiveFeatureExportRow(
                olderRequestedId, SOURCE_ID, SubjectType.SOURCE, "object-1", "{}"),
            new ActiveFeatureExportRow(
                distinctId, SOURCE_ID, SubjectType.SOURCE, "object-2", "{}"));

    var result =
        strategy.finalizeRows(elementSelection(latestId, olderRequestedId, distinctId), raw);

    var resultIds = result.stream().map(ActiveFeatureExportRow::id).toList();
    assertEquals(List.of(latestId, distinctId), resultIds);
    // The older requested ID was silently dropped — no exception, no signal back to the caller.
    assertFalse(resultIds.contains(olderRequestedId));
  }

  @Test
  void finalizeRows_treatsLayerAndSourceWithSameObjectIdAsDistinct() {
    var sourceRowId = UUID.fromString("99999999-9999-9999-9999-999999999994");
    var layerRowId = UUID.fromString("99999999-9999-9999-9999-999999999995");
    var raw =
        List.of(
            new ActiveFeatureExportRow(
                sourceRowId, SOURCE_ID, SubjectType.SOURCE, "object-1", "{}"),
            new ActiveFeatureExportRow(layerRowId, LAYER_ID, SubjectType.LAYER, "object-1", "{}"));

    var result = strategy.finalizeRows(elementSelection(sourceRowId, layerRowId), raw);

    assertEquals(
        List.of(sourceRowId, layerRowId), result.stream().map(ActiveFeatureExportRow::id).toList());
  }

  @Test
  void naming_alwaysProducesSelectionNaming() {
    var request = new ExportRequest(ExportRequest.Format.CSV, null, List.of(ELEMENT_ONE));
    var selection = elementSelection(ELEMENT_ONE);
    when(filenameFactory.createSelection(EXPORT_ID, GENERATED_AT, ".csv"))
        .thenReturn("selection.csv");

    var naming = strategy.naming(request, selection);
    assertNotNull(naming);

    assertEquals("SELECTION", naming.subjectType());
    assertEquals(EXPORT_ID, naming.subjectId(EXPORT_ID));
    assertEquals("selection.csv", naming.filename(EXPORT_ID, GENERATED_AT, ".csv"));
  }

  private static ExportSelection elementSelection(UUID... ids) {
    return new ExportSelection(ExportSelection.Mode.ELEMENT_IDS, List.of(), List.of(ids));
  }

  private static ActiveFeatureExportRow row(UUID id, UUID sourceId, SubjectType sourceType) {
    return new ActiveFeatureExportRow(id, sourceId, sourceType, "obj", "{}");
  }
}
