// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.export.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.opencop.core.api.general.business.policy.ResourceLifecycleGuard;
import com.opencop.core.api.general.ports.gateway.export.ActiveFeatureExportGateway;
import com.opencop.core.api.general.ports.gateway.export.ActiveFeatureExportGateway.LayerSubject;
import com.opencop.core.api.general.technical.model.export.ExportRequest;
import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@SuppressWarnings("unchecked")
class SubjectsExportStrategyTest {

  private static final UUID LAYER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID SOURCE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID OTHER_LAYER_ID =
      UUID.fromString("33333333-3333-3333-3333-333333333333");
  private static final UUID EXPORT_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
  private static final OffsetDateTime GENERATED_AT =
      OffsetDateTime.of(2026, 6, 8, 12, 0, 0, 0, ZoneOffset.UTC);

  private ResourceLifecycleGuard lifecycleGuard;
  private ExportAccessPolicy accessPolicy;
  private ActiveFeatureExportGateway gateway;
  private ExportFilenameFactory filenameFactory;
  private SubjectsExportStrategy strategy;

  @BeforeEach
  void setUp() {
    lifecycleGuard = mock(ResourceLifecycleGuard.class);
    accessPolicy = mock(ExportAccessPolicy.class);
    gateway = mock(ActiveFeatureExportGateway.class);
    filenameFactory = mock(ExportFilenameFactory.class);
    strategy = new SubjectsExportStrategy(lifecycleGuard, accessPolicy, gateway, filenameFactory);
  }

  @Test
  void supportedMode_isSubjects() {
    assertEquals(ExportSelection.Mode.SUBJECTS, strategy.supportedMode());
  }

  /**
   * Contract: direct subject export verifies the subject exists but does NOT enforce the "archived
   * is read-only" lifecycle rule. Archived layers/sources can be exported directly so analysts can
   * review their historical rows. Transitive archived data is still filtered at the gateway/SQL
   * layer for LAYER + includeTransitiveData=true requests.
   */
  @Test
  void assertActive_callsExistsGuardPerSubjectTypeAndNeverArchivedCheck() {
    var selection = subjectsSelection(layerSubject(LAYER_ID, false), sourceSubject(SOURCE_ID));

    strategy.assertActive(selection);

    verify(lifecycleGuard).assertLayerExists(LAYER_ID);
    verify(lifecycleGuard).assertDataSourceExists(SOURCE_ID);
    verify(lifecycleGuard, never()).assertLayerActive(any(), any());
    verify(lifecycleGuard, never()).assertDataSourceActive(any(), any());
  }

  @Test
  void assertAuthorizedPreFetch_withSingleLayerSubject_authorizesSingleLayer() {
    var principal = mock(OpenCopPrincipal.class);
    var selection = subjectsSelection(layerSubject(LAYER_ID, false));

    strategy.assertAuthorizedPreFetch(principal, selection);

    verify(accessPolicy).assertCanExportLayer(principal, LAYER_ID);
    verify(accessPolicy, never()).assertCanExportLayers(any(), any());
  }

  @Test
  void assertAuthorizedPreFetch_withSingleSourceSubject_authorizesSingleSource() {
    var principal = mock(OpenCopPrincipal.class);
    var selection = subjectsSelection(sourceSubject(SOURCE_ID));

    strategy.assertAuthorizedPreFetch(principal, selection);

    verify(accessPolicy).assertCanExportSource(principal, SOURCE_ID);
    verify(accessPolicy, never()).assertCanExportSources(any(), any());
  }

  @Test
  void assertAuthorizedPreFetch_withMultipleSubjects_authorizesByType() {
    var principal = mock(OpenCopPrincipal.class);
    var selection =
        subjectsSelection(
            layerSubject(LAYER_ID, false),
            layerSubject(OTHER_LAYER_ID, true),
            sourceSubject(SOURCE_ID));

    strategy.assertAuthorizedPreFetch(principal, selection);

    verify(accessPolicy).assertCanExportLayers(principal, List.of(LAYER_ID, OTHER_LAYER_ID));
    verify(accessPolicy).assertCanExportSources(principal, List.of(SOURCE_ID));
    verify(accessPolicy, never()).assertCanExportLayer(any(), any());
    verify(accessPolicy, never()).assertCanExportSource(any(), any());
  }

  @Test
  void assertAuthorizedPreFetch_withOnlyLayersInCollection_doesNotCallSourcePolicy() {
    var principal = mock(OpenCopPrincipal.class);
    var selection =
        subjectsSelection(layerSubject(LAYER_ID, false), layerSubject(OTHER_LAYER_ID, true));

    strategy.assertAuthorizedPreFetch(principal, selection);

    verify(accessPolicy).assertCanExportLayers(principal, List.of(LAYER_ID, OTHER_LAYER_ID));
    verify(accessPolicy, never()).assertCanExportSources(any(), any());
  }

  @Test
  void readRows_withSingleLayerSubject_callsScalarLayerGateway() {
    var rows = List.of(row(LAYER_ID, SubjectType.LAYER));
    when(gateway.readLayerRows(LAYER_ID, true)).thenReturn(rows);

    var result = strategy.readRows(subjectsSelection(layerSubject(LAYER_ID, true)));

    assertEquals(rows, result);
    verify(gateway, never()).readLayerRows(any(List.class));
  }

  @Test
  void readRows_withSingleSourceSubject_callsScalarSourceGateway() {
    var rows = List.of(row(SOURCE_ID, SubjectType.SOURCE));
    when(gateway.readSourceRows(SOURCE_ID)).thenReturn(rows);

    var result = strategy.readRows(subjectsSelection(sourceSubject(SOURCE_ID)));

    assertEquals(rows, result);
    verify(gateway, never()).readSourceRows(any(List.class));
  }

  @Test
  void readRows_withMixedSubjects_concatenatesSourceThenLayerRows() {
    var sourceRow = row(SOURCE_ID, SubjectType.SOURCE);
    var layerRow = row(LAYER_ID, SubjectType.LAYER);
    when(gateway.readSourceRows(List.of(SOURCE_ID))).thenReturn(List.of(sourceRow));
    when(gateway.readLayerRows(List.of(new LayerSubject(LAYER_ID, true))))
        .thenReturn(List.of(layerRow));

    var result =
        strategy.readRows(
            subjectsSelection(sourceSubject(SOURCE_ID), layerSubject(LAYER_ID, true)));

    assertEquals(List.of(sourceRow, layerRow), result);
  }

  @Test
  void readRows_withMultipleSubjectsButOnlyLayers_skipsSourceGateway() {
    var layerRow = row(LAYER_ID, SubjectType.LAYER);
    when(gateway.readLayerRows(
            List.of(new LayerSubject(LAYER_ID, false), new LayerSubject(OTHER_LAYER_ID, false))))
        .thenReturn(List.of(layerRow));

    var result =
        strategy.readRows(
            subjectsSelection(layerSubject(LAYER_ID, false), layerSubject(OTHER_LAYER_ID, false)));

    verify(gateway)
        .readLayerRows(
            List.of(new LayerSubject(LAYER_ID, false), new LayerSubject(OTHER_LAYER_ID, false)));
    verify(gateway, never()).readSourceRows(any(List.class));
    assertEquals(List.of(layerRow), result);
  }

  /**
   * Contract: when subject traversal paths overlap (canonical case: subjects = [SOURCE S, LAYER L
   * includeTransitiveData=true] where S is assigned to L) the gateway returns the same feature row
   * through both queries. finalizeRows must drop the duplicate by row id so each feature shows up
   * exactly once in the CSV. Single-subject reads must pass through unchanged.
   */
  @Test
  void finalizeRows_dedupsByRowIdSoOverlappingSubjectsDoNotProduceDuplicateRows() {
    var sharedRowId = UUID.fromString("88888888-8888-8888-8888-888888888881");
    var layerOwnedRowId = UUID.fromString("88888888-8888-8888-8888-888888888882");
    var raw =
        List.of(
            // Returned from the source-batch query (direct SOURCE subject).
            new ActiveFeatureExportRow(
                sharedRowId, SOURCE_ID, SubjectType.SOURCE, "object-shared", "{}"),
            // Returned again from the layer-batch query (transitive resolution of LAYER subject).
            new ActiveFeatureExportRow(
                sharedRowId, SOURCE_ID, SubjectType.SOURCE, "object-shared", "{}"),
            // Layer-owned row, only appears once.
            new ActiveFeatureExportRow(
                layerOwnedRowId, LAYER_ID, SubjectType.LAYER, "object-layer-owned", "{}"));

    var result =
        strategy.finalizeRows(
            subjectsSelection(sourceSubject(SOURCE_ID), layerSubject(LAYER_ID, true)), raw);

    assertEquals(2, result.size());
    assertEquals(
        List.of(sharedRowId, layerOwnedRowId),
        result.stream().map(ActiveFeatureExportRow::id).toList());
  }

  @Test
  void finalizeRows_withSingleSubjectAndNoDuplicates_passesRowsThroughUnchanged() {
    var rowId = UUID.fromString("88888888-8888-8888-8888-888888888883");
    var raw =
        List.of(new ActiveFeatureExportRow(rowId, SOURCE_ID, SubjectType.SOURCE, "object", "{}"));

    var result = strategy.finalizeRows(subjectsSelection(sourceSubject(SOURCE_ID)), raw);

    assertEquals(raw, result);
  }

  @Test
  void assertRowsIntegrity_isNoOp() {
    strategy.assertRowsIntegrity(
        subjectsSelection(layerSubject(LAYER_ID, false)),
        List.of(row(LAYER_ID, SubjectType.LAYER)));

    verifyNoInteractions(gateway, accessPolicy, lifecycleGuard, filenameFactory);
  }

  @Test
  void assertAuthorizedPostFetch_isNoOp() {
    strategy.assertAuthorizedPostFetch(
        mock(OpenCopPrincipal.class),
        subjectsSelection(layerSubject(LAYER_ID, false)),
        List.of(row(LAYER_ID, SubjectType.LAYER)));

    verifyNoInteractions(accessPolicy);
  }

  @Test
  void naming_returnsSelectionNaming() {
    var request =
        new ExportRequest(
            ExportRequest.Format.CSV,
            List.of(new ExportRequest.Subject(SubjectType.LAYER, LAYER_ID, null)),
            null);
    var selection = subjectsSelection(layerSubject(LAYER_ID, false));
    when(filenameFactory.createSelection(EXPORT_ID, GENERATED_AT, ".csv"))
        .thenReturn("selection.csv");

    var naming = strategy.naming(request, selection);

    assertEquals("SELECTION", naming.subjectType());
    assertEquals(EXPORT_ID, naming.subjectId(EXPORT_ID));
    assertEquals("selection.csv", naming.filename(EXPORT_ID, GENERATED_AT, ".csv"));
  }

  private static ExportSelection subjectsSelection(ExportSelection.SubjectSelection... subjects) {
    return new ExportSelection(ExportSelection.Mode.SUBJECTS, List.of(subjects), List.of());
  }

  private static ExportSelection.SubjectSelection layerSubject(UUID id, boolean includeTransitive) {
    return new ExportSelection.SubjectSelection(SubjectType.LAYER, id, includeTransitive);
  }

  private static ExportSelection.SubjectSelection sourceSubject(UUID id) {
    return new ExportSelection.SubjectSelection(SubjectType.SOURCE, id, false);
  }

  private static ActiveFeatureExportRow row(UUID sourceId, SubjectType sourceType) {
    return new ActiveFeatureExportRow(UUID.randomUUID(), sourceId, sourceType, "object", "{}");
  }
}
