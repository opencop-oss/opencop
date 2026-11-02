// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.layer;

import com.opencop.core.api.general.technical.endpoint.filter.FilterParams;
import com.opencop.core.api.iam.ports.gateway.repository.LayerDirectoryReader;
import com.opencop.core.sdk.model.iam.layer.LayerDirectoryEntry;
import com.opencop.core.sdk.model.iam.layer.LayerDirectoryFilterField;
import com.opencop.core.sdk.model.iam.layer.LayerDirectoryReadFilter;
import com.opencop.core.sdk.model.visibility.RecordVisibility;
import com.opencop.core.sdk.technical.gateway.db.PageResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves the admin Layers grid: a filtered, sorted, paginated view of the combined
 * layer/layer-group directory. Resolves paging/filtering/sorting server-side so the grid loads a
 * bounded page rather than the whole list.
 */
@RestController
public class LayerDirectoryEndpoint {

  private final LayerDirectoryReader layerDirectoryReader;

  LayerDirectoryEndpoint(LayerDirectoryReader layerDirectoryReader) {
    this.layerDirectoryReader = layerDirectoryReader;
  }

  /**
   * @param timezone IANA zone the grid renders timestamps in, used to resolve the day boundaries of
   *     date-only criteria. Optional; omitting it resolves them in UTC.
   * @param request source of the repeated {@code filter} params, read directly rather than bound —
   *     see {@link FilterParams#from}
   */
  @PreAuthorize("hasRole('admin')")
  @GetMapping("/v1/admin/layer-directory")
  public ResponseEntity<PageResponse<LayerDirectoryEntry>> getLayerDirectory(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) RecordVisibility recordVisibility,
      @RequestParam(required = false) String timezone,
      @PageableDefault(size = 25) Pageable pageable,
      HttpServletRequest request) {
    return ResponseEntity.ok(
        layerDirectoryReader.getPage(
            new LayerDirectoryReadFilter(
                search,
                recordVisibility,
                FilterParams.from(request, LayerDirectoryFilterField::fromWire),
                FilterParams.zoneOf(timezone)),
            pageable));
  }
}
