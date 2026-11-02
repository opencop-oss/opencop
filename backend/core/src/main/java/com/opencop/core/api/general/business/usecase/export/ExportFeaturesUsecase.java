// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.export;

import com.opencop.core.api.general.business.export.ActiveFeatureExportRow;
import com.opencop.core.api.general.business.export.ExportArtifact;
import com.opencop.core.api.general.business.export.ExportFormatResolver;
import com.opencop.core.api.general.business.export.ExportSelection;
import com.opencop.core.api.general.business.model.Caller;
import com.opencop.core.api.general.business.model.audit.AuditEntry.EntityType;
import com.opencop.core.api.general.business.model.audit.AuditEntry.Operation;
import com.opencop.core.api.general.business.model.audit.AuditEvent;
import com.opencop.core.api.general.business.usecase.connection.DataSourceAuditProjection;
import com.opencop.core.api.general.business.usecase.export.strategy.ExportSelectionStrategies;
import com.opencop.core.api.general.business.usecase.layer.LayerAuditProjection;
import com.opencop.core.api.general.ports.gateway.audit.AuditRecorder;
import com.opencop.core.api.general.ports.gateway.export.ExportArtifactRepository;
import com.opencop.core.api.general.ports.usecase.export.ExportFeatures;
import com.opencop.core.api.general.technical.model.export.ExportRequest;
import com.opencop.core.api.general.technical.model.export.ExportResponse;
import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.ports.gateway.blob.BlobClient;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class ExportFeaturesUsecase implements ExportFeatures {

  private static final String EXPORTS_BLOB_PREFIX = "exports/";
  private static final String DOWNLOAD_PATH_PREFIX = "/v1/exports/";
  private static final String DOWNLOAD_PATH_SUFFIX = "/download";
  private static final long EXPORT_TTL_HOURS = 1;

  private final ExportSelectionStrategies strategies;
  private final ExportFormatResolver formatResolver;
  private final ExportArtifactRepository artifactRepository;
  private final BlobClient blobClient;
  private final AuditRecorder auditRecorder;
  private final DataSourceAuditProjection sourceProjection;
  private final LayerAuditProjection layerProjection;
  private final TransactionTemplate transactionTemplate;
  private final Clock clock;

  public ExportFeaturesUsecase(
      ExportSelectionStrategies strategies,
      ExportFormatResolver formatResolver,
      ExportArtifactRepository artifactRepository,
      BlobClient blobClient,
      AuditRecorder auditRecorder,
      DataSourceAuditProjection sourceProjection,
      LayerAuditProjection layerProjection,
      PlatformTransactionManager transactionManager,
      Clock clock) {
    this.strategies = strategies;
    this.formatResolver = formatResolver;
    this.artifactRepository = artifactRepository;
    this.blobClient = blobClient;
    this.auditRecorder = auditRecorder;
    this.sourceProjection = sourceProjection;
    this.layerProjection = layerProjection;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
    this.clock = clock;
  }

  @Override
  public ExportResponse create(OpenCopPrincipal principal, ExportRequest request) {
    Objects.requireNonNull(principal, "principal");
    var selection = validate(request);
    var strategy = strategies.forMode(selection.mode());

    var exportId = UUID.randomUUID();
    var generatedAt = OffsetDateTime.now(clock);
    var expiresAt = generatedAt.plusHours(EXPORT_TTL_HOURS);
    var mapper = formatResolver.mapperFor(request.format());

    strategy.assertActive(selection);
    strategy.assertAuthorizedPreFetch(principal, selection);
    var rows = strategy.readRows(selection);
    strategy.assertRowsIntegrity(selection, rows);
    strategy.assertAuthorizedPostFetch(principal, selection, rows);
    rows = strategy.finalizeRows(selection, rows);

    var naming = strategy.naming(request, selection);
    var filename = naming.filename(exportId, generatedAt, mapper.fileExtension());
    var blobPath = EXPORTS_BLOB_PREFIX + exportId + mapper.fileExtension();
    var bytes = mapper.map(rows);
    blobClient.upload(blobPath, new ByteArrayInputStream(bytes));
    var exportedRows = rows;
    try {
      // The history rows commit with the artifact they describe: an export the artifact table
      // never recorded is one the operator cannot download, so it did not happen.
      transactionTemplate.executeWithoutResult(
          _ -> {
            artifactRepository.save(
                new ExportArtifact(
                    exportId,
                    naming.subjectType(),
                    naming.subjectId(exportId),
                    principal.internalUserId(),
                    request.format().name(),
                    blobPath,
                    filename,
                    generatedAt,
                    expiresAt));
            recordExported(exportId, request, selection, exportedRows, principal);
          });
    } catch (RuntimeException saveFailure) {
      try {
        blobClient.delete(blobPath);
      } catch (RuntimeException cleanupFailure) {
        saveFailure.addSuppressed(cleanupFailure);
      }
      throw saveFailure;
    }
    return new ExportResponse(
        exportId,
        filename,
        request.format(),
        generatedAt,
        expiresAt,
        DOWNLOAD_PATH_PREFIX + exportId + DOWNLOAD_PATH_SUFFIX);
  }

  /**
   * One row per resource the export drew from, so each drawer shows that its data left the system.
   *
   * <p>Recorded when the export is requested rather than when the file is fetched: the download is
   * a second, separately recorded action, and an export whose link is never followed still read the
   * data. The row names no resource because the export path holds no name lookup; the id is what
   * the drawer it appears in is keyed by anyway.
   */
  private void recordExported(
      UUID exportId,
      ExportRequest request,
      ExportSelection selection,
      List<ActiveFeatureExportRow> rows,
      OpenCopPrincipal principal) {
    var subjects = touchedSubjects(selection, rows);
    var actor = Caller.of(principal);
    var format = request.format().name();
    subjects.forEach(
        (subjectId, subjectType) ->
            this.auditRecorder.record(
                new AuditEvent(
                    subjectType == SubjectType.LAYER ? EntityType.LAYER : EntityType.DATA_SOURCE,
                    subjectId,
                    // An export names subjects by id only, and resolving each one's name would add
                    // a lookup per subject across two managers for a field the drawer already has.
                    null,
                    Operation.EXPORTED,
                    actor,
                    subjectType == SubjectType.LAYER
                        ? this.layerProjection.exported(format, exportId, subjects.size())
                        : this.sourceProjection.exported(format, exportId, subjects.size()),
                    AuditEvent.CURRENT_CHANGE_VERSION)));
  }

  /**
   * What the export read from: the subjects it named, or — for a selection of element ids, which
   * names none — the distinct resources the rows it returned came from.
   */
  private static Map<UUID, SubjectType> touchedSubjects(
      ExportSelection selection, List<ActiveFeatureExportRow> rows) {
    var subjects = new LinkedHashMap<UUID, SubjectType>();
    if (selection.mode() == ExportSelection.Mode.SUBJECTS) {
      selection
          .subjects()
          .forEach(subject -> subjects.put(subject.subjectId(), subject.subjectType()));
      return subjects;
    }
    rows.forEach(row -> subjects.put(row.sourceId(), row.sourceType()));
    return subjects;
  }

  private static ExportSelection validate(ExportRequest request) {
    if (request == null) {
      throw new GlobalOpenCopException("Export request must not be null", HttpStatus.BAD_REQUEST);
    }
    if (request.format() == null) {
      throw new GlobalOpenCopException("Export request format is required", HttpStatus.BAD_REQUEST);
    }
    return ExportSelection.from(request);
  }
}
