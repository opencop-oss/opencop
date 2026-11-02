// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.export;

import com.opencop.core.api.general.business.export.ExportArtifact;
import com.opencop.core.api.general.business.export.ExportFormatResolver;
import com.opencop.core.api.general.ports.gateway.export.ExportArtifactRepository;
import com.opencop.core.api.general.ports.gateway.export.ExportDownloadUrlGateway;
import com.opencop.core.api.general.ports.usecase.export.DownloadExport;
import com.opencop.core.api.general.ports.usecase.export.DownloadExport.DownloadRedirect;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class DownloadExportUsecase implements DownloadExport {

  private final ExportArtifactRepository artifactRepository;
  private final ExportFormatResolver formatResolver;
  private final ExportDownloadUrlGateway downloadUrlGateway;
  private final Clock clock;

  public DownloadExportUsecase(
      ExportArtifactRepository artifactRepository,
      ExportFormatResolver formatResolver,
      ExportDownloadUrlGateway downloadUrlGateway,
      Clock clock) {
    this.artifactRepository = artifactRepository;
    this.formatResolver = formatResolver;
    this.downloadUrlGateway = downloadUrlGateway;
    this.clock = clock;
  }

  @Override
  public DownloadRedirect download(OpenCopPrincipal principal, UUID exportId) {
    Objects.requireNonNull(principal, "principal");
    var artifact =
        artifactRepository
            .findById(exportId)
            .orElseThrow(
                () -> new GlobalOpenCopException("Export not found", HttpStatus.NOT_FOUND));
    assertCreator(principal, artifact);
    assertNotExpired(artifact);
    var contentType = formatResolver.contentTypeFor(artifact.format());
    return new DownloadRedirect(
        artifact.filename(),
        contentType,
        downloadUrlGateway.createSignedUrl(
            artifact.blobPath(), artifact.filename(), contentType, artifact.expiresAt()));
  }

  private static void assertCreator(OpenCopPrincipal principal, ExportArtifact artifact) {
    if (!artifact.creatorId().equals(principal.internalUserId())) {
      throw new GlobalOpenCopException("Export access denied", HttpStatus.FORBIDDEN);
    }
  }

  private void assertNotExpired(ExportArtifact artifact) {
    if (!artifact.expiresAt().isAfter(OffsetDateTime.now(clock))) {
      throw new GlobalOpenCopException("Export expired", HttpStatus.GONE);
    }
  }
}
