// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.gateway.export;

import com.opencop.core.api.general.business.export.ActiveFeatureExportRow;
import java.util.List;
import java.util.UUID;

public interface ActiveFeatureExportGateway {

  List<ActiveFeatureExportRow> readSourceRows(UUID sourceId);

  List<ActiveFeatureExportRow> readSourceRows(List<UUID> sourceIds);

  List<ActiveFeatureExportRow> readLayerRows(UUID layerId, boolean includeTransitiveData);

  List<ActiveFeatureExportRow> readLayerRows(List<LayerSubject> layers);

  List<ActiveFeatureExportRow> readElementRows(List<UUID> elementIds);

  record LayerSubject(UUID layerId, boolean includeTransitiveData) {}
}
