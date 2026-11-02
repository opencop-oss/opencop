// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.export.strategy;

import com.opencop.core.api.general.business.export.ActiveFeatureExportRow;
import com.opencop.core.api.general.business.export.ExportFilenameFactory;
import com.opencop.core.api.general.business.export.ExportSelection;
import com.opencop.core.api.general.business.export.ExportSelection.SubjectSelection;
import com.opencop.core.api.general.business.policy.ExportAccessPolicy;
import com.opencop.core.api.general.business.policy.ResourceLifecycleGuard;
import com.opencop.core.api.general.ports.gateway.export.ActiveFeatureExportGateway;
import com.opencop.core.api.general.ports.gateway.export.ActiveFeatureExportGateway.LayerSubject;
import com.opencop.core.api.general.technical.model.export.ExportRequest;
import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

@Component
public class SubjectsExportStrategy implements ExportSelectionStrategy {

  private final ResourceLifecycleGuard lifecycleGuard;
  private final ExportAccessPolicy accessPolicy;
  private final ActiveFeatureExportGateway gateway;
  private final ExportFilenameFactory filenameFactory;

  public SubjectsExportStrategy(
      ResourceLifecycleGuard lifecycleGuard,
      ExportAccessPolicy accessPolicy,
      ActiveFeatureExportGateway gateway,
      ExportFilenameFactory filenameFactory) {
    this.lifecycleGuard = lifecycleGuard;
    this.accessPolicy = accessPolicy;
    this.gateway = gateway;
    this.filenameFactory = filenameFactory;
  }

  @Override
  public ExportSelection.Mode supportedMode() {
    return ExportSelection.Mode.SUBJECTS;
  }

  @Override
  public void assertActive(ExportSelection selection) {
    // Export is a read-only path: archived layers/sources can still be exported directly so
    // analysts can review their historical rows. We only verify existence so unknown ids fail with
    // 404 instead of leaking through to authorization. Transitive archived sources are still
    // filtered out at the gateway/SQL layer for LAYER + includeTransitiveData=true requests.
    selection.subjects().forEach(this::assertSubjectExists);
  }

  @Override
  public void assertAuthorizedPreFetch(OpenCopPrincipal principal, ExportSelection selection) {
    var subjects = selection.subjects();
    if (subjects.size() == 1) {
      assertSingleSubjectAuthorized(principal, subjects.getFirst());
      return;
    }
    var layerIds = subjectIds(subjects, SubjectType.LAYER);
    var sourceIds = subjectIds(subjects, SubjectType.SOURCE);
    if (!layerIds.isEmpty()) {
      accessPolicy.assertCanExportLayers(principal, layerIds);
    }
    if (!sourceIds.isEmpty()) {
      accessPolicy.assertCanExportSources(principal, sourceIds);
    }
  }

  @Override
  public List<ActiveFeatureExportRow> readRows(ExportSelection selection) {
    var subjects = selection.subjects();
    if (subjects.size() == 1) {
      return readSingleSubjectRows(subjects.getFirst());
    }
    var sourceIds = subjectIds(subjects, SubjectType.SOURCE);
    var layers =
        subjects.stream()
            .filter(subject -> subject.subjectType() == SubjectType.LAYER)
            .map(subject -> new LayerSubject(subject.subjectId(), subject.includeTransitiveData()))
            .toList();
    var sourceRows =
        sourceIds.isEmpty() ? List.<ActiveFeatureExportRow>of() : gateway.readSourceRows(sourceIds);
    var layerRows =
        layers.isEmpty() ? List.<ActiveFeatureExportRow>of() : gateway.readLayerRows(layers);
    return Stream.concat(sourceRows.stream(), layerRows.stream()).toList();
  }

  @Override
  public void assertRowsIntegrity(ExportSelection selection, List<ActiveFeatureExportRow> rows) {
    // SUBJECTS mode trusts the gateway: any number of rows (including zero) is valid.
  }

  @Override
  public void assertAuthorizedPostFetch(
      OpenCopPrincipal principal, ExportSelection selection, List<ActiveFeatureExportRow> rows) {
    // Authorization for subjects is fully resolved before fetching rows.
  }

  /**
   * Dedupes by feature row id so the same row never appears twice in the CSV when subject traversal
   * paths overlap. The canonical case: subjects = [SOURCE S, LAYER L with
   * includeTransitiveData=true] where S is assigned to L. The source-batch query returns S's rows,
   * and the layer-batch query returns the same rows again through the transitive subquery. Each
   * gateway query is itself duplicate-free, so deduping by {@code id()} across the concatenation is
   * sufficient. Single-subject reads pass through unchanged (the gateway already returns one row
   * per active event id).
   */
  @Override
  public List<ActiveFeatureExportRow> finalizeRows(
      ExportSelection selection, List<ActiveFeatureExportRow> rows) {
    var seen = new HashSet<UUID>();
    return rows.stream().filter(row -> seen.add(row.id())).toList();
  }

  @Override
  public ExportArtifactNaming naming(ExportRequest request, ExportSelection selection) {
    return new SelectionNaming(filenameFactory);
  }

  private void assertSubjectExists(SubjectSelection subject) {
    switch (subject.subjectType()) {
      case LAYER -> lifecycleGuard.assertLayerExists(subject.subjectId());
      case SOURCE -> lifecycleGuard.assertDataSourceExists(subject.subjectId());
    }
  }

  private void assertSingleSubjectAuthorized(OpenCopPrincipal principal, SubjectSelection subject) {
    switch (subject.subjectType()) {
      case LAYER -> accessPolicy.assertCanExportLayer(principal, subject.subjectId());
      case SOURCE -> accessPolicy.assertCanExportSource(principal, subject.subjectId());
    }
  }

  private List<ActiveFeatureExportRow> readSingleSubjectRows(SubjectSelection subject) {
    return switch (subject.subjectType()) {
      case LAYER -> gateway.readLayerRows(subject.subjectId(), subject.includeTransitiveData());
      case SOURCE -> gateway.readSourceRows(subject.subjectId());
    };
  }

  private static List<UUID> subjectIds(List<SubjectSelection> subjects, SubjectType subjectType) {
    return subjects.stream()
        .filter(subject -> subject.subjectType() == subjectType)
        .map(SubjectSelection::subjectId)
        .toList();
  }
}
