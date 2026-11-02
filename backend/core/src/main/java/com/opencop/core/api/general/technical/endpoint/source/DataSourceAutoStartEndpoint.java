// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.source;

import com.opencop.core.api.general.business.service.DataSourceAutoStartService;
import com.opencop.core.api.general.business.service.DataSourceAutoStartService.AutoStartResult;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin endpoint to trigger auto-start of all enabled data sources.
 *
 * <p>This is useful when running multiple Core instances with auto-start disabled on startup, or
 * when you want to manually trigger the process after adding new data sources.
 */
@RestController
@PreAuthorize("hasRole('admin')")
public class DataSourceAutoStartEndpoint {

  private final DataSourceAutoStartService autoStartService;

  DataSourceAutoStartEndpoint(DataSourceAutoStartService autoStartService) {
    this.autoStartService = autoStartService;
  }

  public record AutoStartResponse(int started, int failed, int total) {
    static AutoStartResponse from(AutoStartResult result) {
      return new AutoStartResponse(
          result.successCount(), result.failCount(), result.totalEnabled());
    }
  }

  @PostMapping("/v1/admin/data-sources/start-enabled")
  public ResponseEntity<AutoStartResponse> autoStartEnabledDataSources() {
    AutoStartResult result = autoStartService.autoStartEnabledDataSources();
    return ResponseEntity.ok(AutoStartResponse.from(result));
  }
}
