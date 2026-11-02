// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.export.strategy;

import com.opencop.core.api.general.business.export.ActiveFeatureExportRow;
import com.opencop.core.api.general.business.export.ExportFilenameFactory;
import com.opencop.core.api.general.business.export.ExportSelection;
import com.opencop.core.api.general.business.policy.ExportAccessPolicy;
import com.opencop.core.api.general.ports.gateway.export.ActiveFeatureExportGateway;
import com.opencop.core.api.general.technical.model.export.ExportRequest;
import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ElementIdsExportStrategy implements ExportSelectionStrategy {

  private static final String ELEMENTS_NOT_FOUND_MESSAGE = "Export elements not found";

  private final ExportAccessPolicy accessPolicy;
  private final ActiveFeatureExportGateway gateway;
  private final ExportFilenameFactory filenameFactory;

  public ElementIdsExportStrategy(
      ExportAccessPolicy accessPolicy,
      ActiveFeatureExportGateway gateway,
      ExportFilenameFactory filenameFactory) {
    this.accessPolicy = accessPolicy;
    this.gateway = gateway;
    this.filenameFactory = filenameFactory;
  }

  @Override
  public ExportSelection.Mode supportedMode() {
    return ExportSelection.Mode.ELEMENT_IDS;
  }

  @Override
  public void assertActive(ExportSelection selection) {
    // Element-id selections bypass per-subject lifecycle checks because authorization
    // is derived from the actual rows returned by the gateway.
  }

  @Override
  public void assertAuthorizedPreFetch(OpenCopPrincipal principal, ExportSelection selection) {
    // Authorization for element-id selections requires the fetched rows.
  }

  @Override
  public List<ActiveFeatureExportRow> readRows(ExportSelection selection) {
    return gateway.readElementRows(selection.elementIds());
  }

  @Override
  public void assertRowsIntegrity(ExportSelection selection, List<ActiveFeatureExportRow> rows) {
    if (rows.size() != selection.elementIds().size()) {
      throw new GlobalOpenCopException(ELEMENTS_NOT_FOUND_MESSAGE, HttpStatus.NOT_FOUND);
    }
  }

  @Override
  public void assertAuthorizedPostFetch(
      OpenCopPrincipal principal, ExportSelection selection, List<ActiveFeatureExportRow> rows) {
    var layerIds = rowSourceIds(rows, SubjectType.LAYER);
    var sourceIds = rowSourceIds(rows, SubjectType.SOURCE);
    if (!layerIds.isEmpty()) {
      accessPolicy.assertCanExportLayers(principal, layerIds);
    }
    if (!sourceIds.isEmpty()) {
      accessPolicy.assertCanExportSources(principal, sourceIds);
    }
  }

  @Override
  public List<ActiveFeatureExportRow> finalizeRows(
      ExportSelection selection, List<ActiveFeatureExportRow> rows) {
    var seen = new HashSet<FeatureKey>();
    return rows.stream()
        .filter(row -> seen.add(new FeatureKey(row.sourceId(), row.objectId())))
        .toList();
  }

  @Override
  public ExportArtifactNaming naming(ExportRequest request, ExportSelection selection) {
    return new SelectionNaming(filenameFactory);
  }

  private static List<UUID> rowSourceIds(
      List<ActiveFeatureExportRow> rows, SubjectType subjectType) {
    return rows.stream()
        .filter(row -> row.sourceType() == subjectType)
        .map(ActiveFeatureExportRow::sourceId)
        .distinct()
        .toList();
  }

  private record FeatureKey(UUID sourceId, String objectId) {}
}
