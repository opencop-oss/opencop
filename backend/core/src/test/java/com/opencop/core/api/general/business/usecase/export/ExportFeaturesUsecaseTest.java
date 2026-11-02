// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.business.export.ActiveFeatureExportRow;
import com.opencop.core.api.general.business.export.CsvFeatureExportMapper;
import com.opencop.core.api.general.business.export.ExportArtifact;
import com.opencop.core.api.general.business.export.ExportFilenameFactory;
import com.opencop.core.api.general.business.export.ExportFormatResolver;
import com.opencop.core.api.general.business.model.audit.AuditEntry.EntityType;
import com.opencop.core.api.general.business.model.audit.AuditEntry.Operation;
import com.opencop.core.api.general.business.model.audit.AuditEvent;
import com.opencop.core.api.general.business.policy.ExportAccessPolicy;
import com.opencop.core.api.general.business.policy.ResourceLifecycleGuard;
import com.opencop.core.api.general.business.usecase.connection.DataSourceAuditProjection;
import com.opencop.core.api.general.business.usecase.export.strategy.ElementIdsExportStrategy;
import com.opencop.core.api.general.business.usecase.export.strategy.ExportSelectionStrategies;
import com.opencop.core.api.general.business.usecase.export.strategy.SubjectsExportStrategy;
import com.opencop.core.api.general.business.usecase.layer.LayerAuditProjection;
import com.opencop.core.api.general.ports.gateway.audit.AuditRecorder;
import com.opencop.core.api.general.ports.gateway.export.ActiveFeatureExportGateway;
import com.opencop.core.api.general.ports.gateway.export.ActiveFeatureExportGateway.LayerSubject;
import com.opencop.core.api.general.ports.gateway.export.ExportArtifactRepository;
import com.opencop.core.api.general.technical.model.export.ExportRequest;
import com.opencop.core.sdk.component.security.TestAuthentications;
import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.ports.gateway.blob.BlobClient;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;

class ExportFeaturesUsecaseTest {

  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655445001");
  private static final UUID SUBJECT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655445002");
  private static final UUID ROW_ONE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655445003");
  private static final UUID ROW_TWO_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655445004");
  private static final UUID OTHER_SUBJECT_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655445005");
  private static final Instant NOW = Instant.parse("2026-06-08T12:00:00Z");
  private static final String CSV_HEADER =
      "id,sourceId,sourceType,objectId,displayName,time,start,stale,authorFirstName,authorLastName,authorUsername,elementType,symbol,fillColor,strokeColor,strokeStyle,strokeWeight,radius,geometry,attributes,href\n";

  private ResourceLifecycleGuard lifecycleGuard;
  private ExportAccessPolicy accessPolicy;
  private ActiveFeatureExportGateway activeFeatureExportGateway;
  private ExportArtifactRepository artifactRepository;
  private BlobClient blobClient;
  private MutableClock clock;
  private AuditRecorder auditRecorder;
  private ExportFeaturesUsecase usecase;

  @BeforeEach
  void setUp() {
    lifecycleGuard = mock(ResourceLifecycleGuard.class);
    accessPolicy = mock(ExportAccessPolicy.class);
    activeFeatureExportGateway = mock(ActiveFeatureExportGateway.class);
    artifactRepository = mock(ExportArtifactRepository.class);
    blobClient = mock(BlobClient.class);
    auditRecorder = mock(AuditRecorder.class);
    clock = new MutableClock(NOW);
    var filenameFactory = new ExportFilenameFactory();
    var subjectsStrategy =
        new SubjectsExportStrategy(
            lifecycleGuard, accessPolicy, activeFeatureExportGateway, filenameFactory);
    var elementIdsStrategy =
        new ElementIdsExportStrategy(accessPolicy, activeFeatureExportGateway, filenameFactory);
    var strategies = new ExportSelectionStrategies(List.of(subjectsStrategy, elementIdsStrategy));
    usecase =
        new ExportFeaturesUsecase(
            strategies,
            new ExportFormatResolver(List.of(new CsvFeatureExportMapper())),
            artifactRepository,
            blobClient,
            auditRecorder,
            new DataSourceAuditProjection(),
            new LayerAuditProjection(),
            mock(PlatformTransactionManager.class),
            clock);
  }

  @Test
  void create_whenLayerIsMissing_returnsNotFoundBeforeExportWork() {
    var request = layerRequest(SUBJECT_ID, null);
    var principal = principal();
    doThrow(new GlobalOpenCopException("Resource not found", HttpStatus.NOT_FOUND))
        .when(lifecycleGuard)
        .assertLayerExists(SUBJECT_ID);

    var exception =
        assertThrows(GlobalOpenCopException.class, () -> usecase.create(principal, request));

    assertEquals(HttpStatus.NOT_FOUND, exception.getHttpStatus());
    verifyNoInteractions(accessPolicy);
    verify(artifactRepository, never()).save(any());
    verifyNoInteractions(blobClient);
  }

  @Test
  void create_whenLayerRoleIsMember_returnsForbiddenBeforeExportWork() {
    var request = layerRequest(SUBJECT_ID, null);
    var principal = principal();
    doThrow(new GlobalOpenCopException("Insufficient permissions", HttpStatus.FORBIDDEN))
        .when(accessPolicy)
        .assertCanExportLayer(principal, SUBJECT_ID);

    var exception =
        assertThrows(GlobalOpenCopException.class, () -> usecase.create(principal, request));

    assertEquals(HttpStatus.FORBIDDEN, exception.getHttpStatus());
    verify(lifecycleGuard).assertLayerExists(SUBJECT_ID);
    verify(accessPolicy).assertCanExportLayer(principal, SUBJECT_ID);
    verify(artifactRepository, never()).save(any());
    verifyNoInteractions(blobClient);
  }

  @Test
  void create_whenSourceOwnerIsAuthorized_returnsMetadataAfterUploadingBlob() {
    var request = sourceRequest(SUBJECT_ID, null);
    var principal = principal();
    when(activeFeatureExportGateway.readSourceRows(SUBJECT_ID)).thenReturn(List.of());

    var response = usecase.create(principal, request);

    assertNotNull(response.exportId());
    assertEquals(ExportRequest.Format.CSV, response.format());
    assertEquals("/v1/exports/" + response.exportId() + "/download", response.downloadPath());
    assertEquals(
        "20260608T120000Z-selection-" + response.exportId().toString().substring(0, 8) + ".csv",
        response.filename());
    verify(lifecycleGuard).assertDataSourceExists(SUBJECT_ID);
    verify(blobClient).upload(eq("exports/" + response.exportId() + ".csv"), any());
    var artifactCaptor = ArgumentCaptor.forClass(ExportArtifact.class);
    verify(artifactRepository).save(artifactCaptor.capture());
    assertEquals(response.exportId(), artifactCaptor.getValue().exportId());
    assertEquals(USER_ID, artifactCaptor.getValue().creatorId());
    assertEquals("SELECTION", artifactCaptor.getValue().subjectType());
    assertEquals(response.exportId(), artifactCaptor.getValue().subjectId());
    assertEquals(response.filename(), artifactCaptor.getValue().filename());
    verify(accessPolicy).assertCanExportSource(principal, SUBJECT_ID);
  }

  @Test
  void create_whenSourceRequestSetsIncludeDataSourceDataFalse_exportsSourceRows() {
    var request = sourceRequest(SUBJECT_ID, false);
    var principal = principal();
    when(activeFeatureExportGateway.readSourceRows(SUBJECT_ID)).thenReturn(List.of());

    var response = usecase.create(principal, request);

    assertNotNull(response.exportId());
    verify(activeFeatureExportGateway).readSourceRows(SUBJECT_ID);
    verify(activeFeatureExportGateway, never()).readLayerRows(any(), anyBoolean());
    verify(blobClient).upload(eq("exports/" + response.exportId() + ".csv"), any());
  }

  @Test
  void create_whenArtifactSaveFails_deletesUploadedBlobAndRethrows() {
    var request = sourceRequest(SUBJECT_ID, null);
    var principal = principal();
    when(activeFeatureExportGateway.readSourceRows(SUBJECT_ID)).thenReturn(List.of());
    var saveFailure = new RuntimeException("db down");
    doThrow(saveFailure).when(artifactRepository).save(any());

    var thrown = assertThrows(RuntimeException.class, () -> usecase.create(principal, request));

    assertEquals(saveFailure, thrown);
    var blobPathCaptor = ArgumentCaptor.forClass(String.class);
    verify(blobClient).upload(blobPathCaptor.capture(), any());
    verify(blobClient).delete(blobPathCaptor.getValue());
  }

  @Test
  void create_whenArtifactSaveAndBlobDeleteBothFail_rethrowsSaveErrorWithCleanupSuppressed() {
    var request = sourceRequest(SUBJECT_ID, null);
    var principal = principal();
    when(activeFeatureExportGateway.readSourceRows(SUBJECT_ID)).thenReturn(List.of());
    var saveFailure = new RuntimeException("db down");
    var cleanupFailure = new RuntimeException("blob unavailable");
    doThrow(saveFailure).when(artifactRepository).save(any());
    doThrow(cleanupFailure).when(blobClient).delete(any());

    var thrown = assertThrows(RuntimeException.class, () -> usecase.create(principal, request));

    assertEquals(saveFailure, thrown);
    assertEquals(1, thrown.getSuppressed().length);
    assertEquals(cleanupFailure, thrown.getSuppressed()[0]);
  }

  @Test
  void create_whenSourceRequestSetsIncludeTransitiveDataTrue_returnsBadRequestBeforeExportWork() {
    var request = sourceRequest(SUBJECT_ID, true);
    var principal = principal();

    var exception =
        assertThrows(GlobalOpenCopException.class, () -> usecase.create(principal, request));

    assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
    assertEquals("Data sources do not have transitive data", exception.getMessage());
    verifyNoInteractions(lifecycleGuard, accessPolicy, activeFeatureExportGateway);
    verify(artifactRepository, never()).save(any());
    verifyNoInteractions(blobClient);
  }

  @Test
  void create_whenAuthorizedLayerHasTwoActiveRows_uploadsCsvAndSavesBackendMetadata()
      throws Exception {
    var request = layerRequest(SUBJECT_ID, null);
    var principal = principal();
    when(activeFeatureExportGateway.readLayerRows(SUBJECT_ID, false))
        .thenReturn(List.of(rowOne(), rowTwo()));

    var response = usecase.create(principal, request);

    assertEquals("/v1/exports/" + response.exportId() + "/download", response.downloadPath());
    var uploadCaptor = ArgumentCaptor.forClass(InputStream.class);
    verify(blobClient)
        .upload(eq("exports/" + response.exportId() + ".csv"), uploadCaptor.capture());
    var csv = new String(uploadCaptor.getValue().readAllBytes(), StandardCharsets.UTF_8);
    assertEquals(
        CSV_HEADER
            + ROW_ONE_ID
            + ","
            + SUBJECT_ID
            + ",SOURCE"
            + ",object-alpha,Alpha"
            + ",".repeat(15)
            + "{},\n"
            + ROW_TWO_ID
            + ","
            + SUBJECT_ID
            + ",SOURCE"
            + ",object-beta,Beta"
            + ",".repeat(15)
            + "{},\n",
        csv);
    var artifactCaptor = ArgumentCaptor.forClass(ExportArtifact.class);
    verify(artifactRepository).save(artifactCaptor.capture());
    assertEquals(response.exportId(), artifactCaptor.getValue().exportId());
    assertEquals("exports/" + response.exportId() + ".csv", artifactCaptor.getValue().blobPath());
    verify(accessPolicy).assertCanExportLayer(principal, SUBJECT_ID);
    verify(activeFeatureExportGateway).readLayerRows(SUBJECT_ID, false);
    verify(activeFeatureExportGateway, never()).readSourceRows(SUBJECT_ID);
  }

  @Test
  void create_whenLayerRequestIncludesDataSourceData_readsLayerRowsWithAssignedSources()
      throws Exception {
    var request = layerRequest(SUBJECT_ID, true);
    when(activeFeatureExportGateway.readLayerRows(SUBJECT_ID, true)).thenReturn(List.of(rowOne()));

    usecase.create(principal(), request);

    verify(activeFeatureExportGateway).readLayerRows(SUBJECT_ID, true);
    verify(activeFeatureExportGateway, never()).readSourceRows(SUBJECT_ID);
  }

  @Test
  void create_whenAuthorizedSubjectSelection_exportsRowsAndStoresSelectionMetadata()
      throws Exception {
    var request =
        new ExportRequest(
            ExportRequest.Format.CSV,
            List.of(
                new ExportRequest.Subject(SubjectType.SOURCE, SUBJECT_ID, null),
                new ExportRequest.Subject(SubjectType.LAYER, OTHER_SUBJECT_ID, true)),
            null);
    var principal = principal();
    var sourceRows = List.of(rowOne());
    var layerRows = List.of(row(ROW_TWO_ID, OTHER_SUBJECT_ID, SubjectType.LAYER, "layer", "Beta"));
    when(activeFeatureExportGateway.readSourceRows(List.of(SUBJECT_ID))).thenReturn(sourceRows);
    when(activeFeatureExportGateway.readLayerRows(
            List.of(new LayerSubject(OTHER_SUBJECT_ID, true))))
        .thenReturn(layerRows);

    var response = usecase.create(principal, request);

    assertTrue(response.filename().contains("-selection-"));
    verify(lifecycleGuard).assertDataSourceExists(SUBJECT_ID);
    verify(lifecycleGuard).assertLayerExists(OTHER_SUBJECT_ID);
    verify(accessPolicy).assertCanExportSources(principal, List.of(SUBJECT_ID));
    verify(accessPolicy).assertCanExportLayers(principal, List.of(OTHER_SUBJECT_ID));
    var uploadCaptor = ArgumentCaptor.forClass(InputStream.class);
    verify(blobClient)
        .upload(eq("exports/" + response.exportId() + ".csv"), uploadCaptor.capture());
    var csv = new String(uploadCaptor.getValue().readAllBytes(), StandardCharsets.UTF_8);
    assertTrue(csv.contains(ROW_ONE_ID + "," + SUBJECT_ID + ",SOURCE,object-alpha,Alpha"));
    assertTrue(csv.contains(ROW_TWO_ID + "," + OTHER_SUBJECT_ID + ",LAYER,layer,Beta"));
    var artifactCaptor = ArgumentCaptor.forClass(ExportArtifact.class);
    verify(artifactRepository).save(artifactCaptor.capture());
    assertEquals("SELECTION", artifactCaptor.getValue().subjectType());
    assertEquals(response.exportId(), artifactCaptor.getValue().subjectId());
    assertEquals(response.filename(), artifactCaptor.getValue().filename());
  }

  @Test
  void create_whenElementIdsMatchAuthorizedRows_exportsRowsUsingDatabaseOwnership()
      throws Exception {
    var elementIds = List.of(ROW_ONE_ID, ROW_TWO_ID);
    var request = new ExportRequest(ExportRequest.Format.CSV, null, elementIds);
    var principal = principal();
    when(activeFeatureExportGateway.readElementRows(elementIds))
        .thenReturn(
            List.of(
                rowOne(), row(ROW_TWO_ID, OTHER_SUBJECT_ID, SubjectType.LAYER, "layer", "Beta")));

    var response = usecase.create(principal, request);

    assertTrue(response.filename().contains("-selection-"));
    verify(accessPolicy).assertCanExportSources(principal, List.of(SUBJECT_ID));
    verify(accessPolicy).assertCanExportLayers(principal, List.of(OTHER_SUBJECT_ID));
    verify(lifecycleGuard, never()).assertLayerExists(any());
    verify(lifecycleGuard, never()).assertDataSourceExists(any());
    verify(lifecycleGuard, never()).assertLayerActive(any(), any());
    verify(lifecycleGuard, never()).assertDataSourceActive(any(), any());
    verify(blobClient).upload(eq("exports/" + response.exportId() + ".csv"), any());
    var artifactCaptor = ArgumentCaptor.forClass(ExportArtifact.class);
    verify(artifactRepository).save(artifactCaptor.capture());
    assertEquals("SELECTION", artifactCaptor.getValue().subjectType());
    assertEquals(response.exportId(), artifactCaptor.getValue().subjectId());
  }

  @Test
  void create_whenSubjectSelectionAuthorizationFails_returnsForbiddenBeforeUpload() {
    var request = layerRequest(SUBJECT_ID, null);
    var principal = principal();
    doThrow(new GlobalOpenCopException("Insufficient permissions", HttpStatus.FORBIDDEN))
        .when(accessPolicy)
        .assertCanExportLayer(principal, SUBJECT_ID);

    var exception =
        assertThrows(GlobalOpenCopException.class, () -> usecase.create(principal, request));

    assertEquals(HttpStatus.FORBIDDEN, exception.getHttpStatus());
    verify(lifecycleGuard).assertLayerExists(SUBJECT_ID);
    verifyNoInteractions(activeFeatureExportGateway);
    verifyNoInteractions(blobClient);
    verify(artifactRepository, never()).save(any());
  }

  @Test
  void create_whenElementRowSourceIsUnauthorized_returnsForbiddenBeforeUpload() {
    var elementIds = List.of(ROW_ONE_ID);
    var request = new ExportRequest(ExportRequest.Format.CSV, null, elementIds);
    var principal = principal();
    when(activeFeatureExportGateway.readElementRows(elementIds)).thenReturn(List.of(rowOne()));
    doThrow(new GlobalOpenCopException("Insufficient permissions", HttpStatus.FORBIDDEN))
        .when(accessPolicy)
        .assertCanExportSources(principal, List.of(SUBJECT_ID));

    var exception =
        assertThrows(GlobalOpenCopException.class, () -> usecase.create(principal, request));

    assertEquals(HttpStatus.FORBIDDEN, exception.getHttpStatus());
    verify(activeFeatureExportGateway).readElementRows(elementIds);
    verifyNoInteractions(blobClient);
    verify(artifactRepository, never()).save(any());
  }

  @Test
  void create_whenElementIdSelectionHasMissingRows_returnsNotFoundBeforeUpload() {
    var elementIds = List.of(ROW_ONE_ID, ROW_TWO_ID);
    var request = new ExportRequest(ExportRequest.Format.CSV, null, elementIds);
    when(activeFeatureExportGateway.readElementRows(elementIds)).thenReturn(List.of(rowOne()));

    var exception =
        assertThrows(GlobalOpenCopException.class, () -> usecase.create(principal(), request));

    assertEquals(HttpStatus.NOT_FOUND, exception.getHttpStatus());
    verifyNoInteractions(accessPolicy);
    verifyNoInteractions(blobClient);
    verify(artifactRepository, never()).save(any());
  }

  /**
   * Contract: the gateway returns events latest first. When two requested elementIds resolve to the
   * same (sourceId, objectId), the export keeps the first row and silently omits the older event.
   * The omission is visible in the CSV, but the endpoint still returns 201 with a normal
   * ExportResponse and no omission field.
   */
  @Test
  void create_whenElementIdsShareSourceAndObject_silentlyOmitsOlderIdFromCsvWithNoResponseSignal()
      throws Exception {
    var olderId = ROW_ONE_ID;
    var newerId = ROW_TWO_ID;
    var elementIds = List.of(olderId, newerId);
    var request = new ExportRequest(ExportRequest.Format.CSV, null, elementIds);
    var newerRow = row(newerId, SUBJECT_ID, SubjectType.SOURCE, "object-alpha", "Newer");
    var olderRow = row(olderId, SUBJECT_ID, SubjectType.SOURCE, "object-alpha", "Older");
    // Gateway returns latest-first within the same (source, object); the strategy keeps the first.
    when(activeFeatureExportGateway.readElementRows(elementIds))
        .thenReturn(List.of(newerRow, olderRow));

    var response = usecase.create(principal(), request);

    assertNotNull(response.exportId());
    assertEquals(ExportRequest.Format.CSV, response.format());
    var uploadCaptor = ArgumentCaptor.forClass(InputStream.class);
    verify(blobClient)
        .upload(eq("exports/" + response.exportId() + ".csv"), uploadCaptor.capture());
    var csv = new String(uploadCaptor.getValue().readAllBytes(), StandardCharsets.UTF_8);
    assertTrue(csv.contains(newerId + "," + SUBJECT_ID + ",SOURCE,object-alpha,Newer"));
    // Older requested ID is silently dropped from the CSV — caller has no way to know.
    assertFalse(csv.contains(olderId + "," + SUBJECT_ID + ",SOURCE,object-alpha,Older"));
    // CSV contains exactly one data row even though the caller requested two IDs.
    assertEquals(1, csv.lines().count() - 1);
  }

  /**
   * Contract: when subjects overlap (canonical case: source S directly selected AND layer L with
   * includeTransitiveData=true where S is assigned to L), the gateway returns the same feature row
   * through both the source-batch and the layer-batch query. The subjects strategy's finalizeRows
   * must dedupe by row id so the CSV contains each feature exactly once.
   */
  @Test
  void create_whenSourceSubjectOverlapsTransitiveLayerSubject_csvContainsEachRowExactlyOnce()
      throws Exception {
    var request =
        new ExportRequest(
            ExportRequest.Format.CSV,
            List.of(
                new ExportRequest.Subject(SubjectType.SOURCE, SUBJECT_ID, null),
                new ExportRequest.Subject(SubjectType.LAYER, OTHER_SUBJECT_ID, true)),
            null);
    var principal = principal();
    // S's feature row, returned by the direct source-batch query.
    var sourceRow = rowOne();
    // The same feature row, returned again by the layer-batch query through transitive resolution.
    var sameRowAgainViaLayer = rowOne();
    when(activeFeatureExportGateway.readSourceRows(List.of(SUBJECT_ID)))
        .thenReturn(List.of(sourceRow));
    when(activeFeatureExportGateway.readLayerRows(
            List.of(new LayerSubject(OTHER_SUBJECT_ID, true))))
        .thenReturn(List.of(sameRowAgainViaLayer));

    var response = usecase.create(principal, request);

    var uploadCaptor = ArgumentCaptor.forClass(InputStream.class);
    verify(blobClient)
        .upload(eq("exports/" + response.exportId() + ".csv"), uploadCaptor.capture());
    var csv = new String(uploadCaptor.getValue().readAllBytes(), StandardCharsets.UTF_8);
    assertEquals(1, csv.lines().count() - 1);
    assertTrue(csv.contains(ROW_ONE_ID + "," + SUBJECT_ID + ",SOURCE,object-alpha,Alpha"));
  }

  /**
   * Contract: a direct request to export an archived layer must succeed. The export use case
   * verifies the layer EXISTS (so unknown ids 404) but does NOT enforce the archived-is-read-only
   * lifecycle rule. Analysts need historical access. Transitive archived sources are still filtered
   * out at the gateway/SQL layer when includeTransitiveData=true is set on the request.
   */
  @Test
  void create_whenLayerSubjectIsArchived_stillExportsDirectlyWithoutInvokingArchivedCheck() {
    var request = layerRequest(SUBJECT_ID, null);
    var principal = principal();
    when(activeFeatureExportGateway.readLayerRows(SUBJECT_ID, false)).thenReturn(List.of(rowOne()));

    var response = usecase.create(principal, request);

    assertNotNull(response.exportId());
    verify(lifecycleGuard).assertLayerExists(SUBJECT_ID);
    verify(lifecycleGuard, never()).assertLayerActive(any(), any());
    verify(blobClient).upload(eq("exports/" + response.exportId() + ".csv"), any());
  }

  /**
   * Contract: a direct request to export an archived data source must succeed for the same reason
   * as the archived-layer case. See sibling test for the full rationale.
   */
  @Test
  void create_whenSourceSubjectIsArchived_stillExportsDirectlyWithoutInvokingArchivedCheck() {
    var request = sourceRequest(SUBJECT_ID, null);
    var principal = principal();
    when(activeFeatureExportGateway.readSourceRows(SUBJECT_ID)).thenReturn(List.of(rowOne()));

    var response = usecase.create(principal, request);

    assertNotNull(response.exportId());
    verify(lifecycleGuard).assertDataSourceExists(SUBJECT_ID);
    verify(lifecycleGuard, never()).assertDataSourceActive(any(), any());
    verify(blobClient).upload(eq("exports/" + response.exportId() + ".csv"), any());
  }

  @Test
  void create_whenSelectionMixesSubjectsAndElementIds_returnsBadRequestBeforeExportWork() {
    var request =
        new ExportRequest(
            ExportRequest.Format.CSV,
            List.of(new ExportRequest.Subject(SubjectType.SOURCE, SUBJECT_ID, null)),
            List.of(ROW_ONE_ID));

    var exception =
        assertThrows(GlobalOpenCopException.class, () -> usecase.create(principal(), request));

    assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
    verifyNoInteractions(lifecycleGuard, accessPolicy, activeFeatureExportGateway);
    verifyNoInteractions(blobClient);
  }

  @Test
  void create_whenGatewayHasNoRows_uploadsHeaderOnlyCsv() throws Exception {
    var request = sourceRequest(SUBJECT_ID, null);
    when(activeFeatureExportGateway.readSourceRows(SUBJECT_ID)).thenReturn(List.of());

    usecase.create(principal(), request);

    var uploadCaptor = ArgumentCaptor.forClass(InputStream.class);
    verify(blobClient).upload(any(), uploadCaptor.capture());
    assertEquals(
        CSV_HEADER, new String(uploadCaptor.getValue().readAllBytes(), StandardCharsets.UTF_8));
  }

  @Test
  void create_whenSameRequestRunsOneSecondApart_returnsUniqueArtifacts() {
    var request = sourceRequest(SUBJECT_ID, null);
    when(activeFeatureExportGateway.readSourceRows(SUBJECT_ID)).thenReturn(List.of());

    var first = usecase.create(principal(), request);
    clock.advanceSeconds(1);
    var second = usecase.create(principal(), request);

    assertNotEquals(first.exportId(), second.exportId());
    assertNotEquals(first.filename(), second.filename());
    assertNotEquals(first.generatedAt(), second.generatedAt());
    var artifactCaptor = ArgumentCaptor.forClass(ExportArtifact.class);
    verify(artifactRepository, times(2)).save(artifactCaptor.capture());
    var artifacts = artifactCaptor.getAllValues();
    assertNotEquals(artifacts.get(0).blobPath(), artifacts.get(1).blobPath());
  }

  @Test
  void create_whenGatewayReturnsTenThousandRows_uploadsFirstAndFinalCsvRows() throws Exception {
    var request = sourceRequest(SUBJECT_ID, null);
    when(activeFeatureExportGateway.readSourceRows(SUBJECT_ID)).thenReturn(manyRows());

    usecase.create(principal(), request);

    var uploadCaptor = ArgumentCaptor.forClass(InputStream.class);
    verify(blobClient).upload(any(), uploadCaptor.capture());
    var csv = new String(uploadCaptor.getValue().readAllBytes(), StandardCharsets.UTF_8);
    assertNotNull(csv);
    assertTrue(csv.contains("object-0,Feature 0"));
    assertTrue(csv.contains("object-9999,Feature 9999"));
  }

  /** A subject selection is recorded against the subject it named, even when it read no rows. */
  @Test
  void create_whenSourceSubjectIsExported_recordsTheExportAgainstThatSource() {
    var request = sourceRequest(SUBJECT_ID, false);
    var principal = principal();
    when(activeFeatureExportGateway.readSourceRows(SUBJECT_ID)).thenReturn(List.of());

    var response = usecase.create(principal, request);

    var recorded = ArgumentCaptor.forClass(AuditEvent.class);
    verify(auditRecorder).record(recorded.capture());
    var event = recorded.getValue();
    assertEquals(EntityType.DATA_SOURCE, event.entityType());
    assertEquals(SUBJECT_ID, event.entityId());
    assertEquals(Operation.EXPORTED, event.operation());
    assertEquals(USER_ID, event.actor().userId());
    assertEquals("CSV", event.change().details().get("format"));
    assertEquals(response.exportId().toString(), event.change().details().get("exportId"));
    assertEquals(1, event.change().details().get("subjectCount"));
  }

  /**
   * A selection of element ids names no subject, so the resources the rows came from are what the
   * export touched — one row each, in the ledger each belongs to.
   */
  @Test
  void create_whenElementIdsSpanASourceAndALayer_recordsOneRowInEachLedger() {
    var elementIds = List.of(ROW_ONE_ID, ROW_TWO_ID);
    var request = new ExportRequest(ExportRequest.Format.CSV, null, elementIds);
    when(activeFeatureExportGateway.readElementRows(elementIds))
        .thenReturn(
            List.of(
                rowOne(), row(ROW_TWO_ID, OTHER_SUBJECT_ID, SubjectType.LAYER, "layer", "Beta")));

    usecase.create(principal(), request);

    var recorded = ArgumentCaptor.forClass(AuditEvent.class);
    verify(auditRecorder, times(2)).record(recorded.capture());
    assertEquals(
        List.of(EntityType.DATA_SOURCE, EntityType.LAYER),
        recorded.getAllValues().stream().map(AuditEvent::entityType).toList());
    assertEquals(
        List.of(SUBJECT_ID, OTHER_SUBJECT_ID),
        recorded.getAllValues().stream().map(AuditEvent::entityId).toList());
    recorded
        .getAllValues()
        .forEach(event -> assertEquals(2, event.change().details().get("subjectCount")));
  }

  /** An export whose artifact never landed is one the operator cannot download. */
  @Test
  void create_whenArtifactSaveFails_recordsNoExport() {
    var request = sourceRequest(SUBJECT_ID, null);
    when(activeFeatureExportGateway.readSourceRows(SUBJECT_ID)).thenReturn(List.of());
    doThrow(new IllegalStateException("save failed"))
        .when(artifactRepository)
        .save(any(ExportArtifact.class));

    assertThrows(IllegalStateException.class, () -> usecase.create(principal(), request));

    verifyNoInteractions(auditRecorder);
  }

  private static ExportRequest layerRequest(UUID layerId, Boolean includeTransitiveData) {
    return new ExportRequest(
        ExportRequest.Format.CSV,
        List.of(new ExportRequest.Subject(SubjectType.LAYER, layerId, includeTransitiveData)),
        null);
  }

  private static ExportRequest sourceRequest(UUID sourceId, Boolean includeTransitiveData) {
    return new ExportRequest(
        ExportRequest.Format.CSV,
        List.of(new ExportRequest.Subject(SubjectType.SOURCE, sourceId, includeTransitiveData)),
        null);
  }

  private static ActiveFeatureExportRow rowOne() {
    return row(ROW_ONE_ID, "object-alpha", "Alpha");
  }

  private static ActiveFeatureExportRow rowTwo() {
    return row(ROW_TWO_ID, "object-beta", "Beta");
  }

  private static List<ActiveFeatureExportRow> manyRows() {
    return IntStream.range(0, 10_000)
        .mapToObj(
            index ->
                row(
                    UUID.nameUUIDFromBytes(("row-" + index).getBytes(StandardCharsets.UTF_8)),
                    "object-" + index,
                    "Feature " + index))
        .toList();
  }

  private static ActiveFeatureExportRow row(UUID rowId, String objectId, String name) {
    return row(rowId, SUBJECT_ID, SubjectType.SOURCE, objectId, name);
  }

  private static ActiveFeatureExportRow row(
      UUID rowId, UUID sourceId, SubjectType sourceType, String objectId, String name) {
    return new ActiveFeatureExportRow(
        rowId,
        sourceId,
        sourceType,
        objectId,
        """
        {"type":"Feature","properties":{"name":"%s","attributes":{}}}
        """
            .formatted(name));
  }

  private static OpenCopPrincipal principal() {
    var authentication = TestAuthentications.oidcUser(USER_ID);
    return (OpenCopPrincipal) authentication.getPrincipal();
  }

  private static final class MutableClock extends Clock {

    private Instant now;

    private MutableClock(Instant now) {
      this.now = now;
    }

    private void advanceSeconds(long seconds) {
      now = now.plusSeconds(seconds);
    }

    @Override
    public ZoneOffset getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return Clock.fixed(now, zone);
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
