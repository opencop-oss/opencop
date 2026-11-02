// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.export;

import com.opencop.core.api.general.ports.usecase.export.DownloadExport;
import com.opencop.core.api.general.ports.usecase.export.ExportFeatures;
import com.opencop.core.api.general.technical.model.export.ExportRequest;
import com.opencop.core.api.general.technical.model.export.ExportResponse;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExportsEndpoint {

  private static final String EXPORTS_PATH = "/v1/exports";
  private static final String DOWNLOAD_PATH = EXPORTS_PATH + "/{exportId}/download";

  private final ExportFeatures exportFeatures;
  private final DownloadExport downloadExport;

  public ExportsEndpoint(ExportFeatures exportFeatures, DownloadExport downloadExport) {
    this.exportFeatures = exportFeatures;
    this.downloadExport = downloadExport;
  }

  @PostMapping(
      value = EXPORTS_PATH,
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<ExportResponse> create(
      @AuthenticationPrincipal OpenCopPrincipal principal, @RequestBody ExportRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(exportFeatures.create(principal, request));
  }

  @GetMapping(value = DOWNLOAD_PATH)
  public ResponseEntity<Void> download(
      @AuthenticationPrincipal OpenCopPrincipal principal, @PathVariable UUID exportId) {
    var download = downloadExport.download(principal, exportId);
    return ResponseEntity.status(HttpStatus.FOUND)
        .location(URI.create(download.signedUrl()))
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .build();
  }
}
