// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.business.export.ExportArtifact;
import com.opencop.core.api.general.business.export.ExportFormatResolver;
import com.opencop.core.api.general.ports.gateway.export.ExportArtifactRepository;
import com.opencop.core.api.general.ports.gateway.export.ExportDownloadUrlGateway;
import com.opencop.core.sdk.component.security.TestAuthentications;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

class DownloadExportUsecaseTest {

  private static final UUID EXPORT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655446001");
  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655446002");
  private static final UUID SUBJECT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655446003");
  private static final Instant NOW = Instant.parse("2026-06-08T12:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  private static final String SIGNED_URL = "https://storage.example/exports/test.csv?sig=abc";
  private static final OffsetDateTime ARTIFACT_EXPIRES_AT =
      OffsetDateTime.ofInstant(NOW.plusSeconds(60), ZoneOffset.UTC);

  private ExportArtifactRepository artifactRepository;
  private ExportFormatResolver formatResolver;
  private ExportDownloadUrlGateway downloadUrlGateway;
  private DownloadExportUsecase usecase;

  @BeforeEach
  void setUp() {
    artifactRepository = mock(ExportArtifactRepository.class);
    formatResolver = mock(ExportFormatResolver.class);
    downloadUrlGateway = mock(ExportDownloadUrlGateway.class);
    usecase =
        new DownloadExportUsecase(artifactRepository, formatResolver, downloadUrlGateway, CLOCK);
  }

  @Test
  void download_whenCreatorMatchesAndExportIsActive_returnsSignedRedirect() throws Exception {
    when(artifactRepository.findById(EXPORT_ID)).thenReturn(Optional.of(activeArtifact(USER_ID)));
    when(formatResolver.contentTypeFor("CSV")).thenReturn(new MediaType("text", "csv"));
    when(downloadUrlGateway.createSignedUrl(
            "exports/test.csv",
            "layer-export.csv",
            new MediaType("text", "csv"),
            ARTIFACT_EXPIRES_AT))
        .thenReturn(SIGNED_URL);

    var result = usecase.download(principal(USER_ID), EXPORT_ID);

    assertEquals("layer-export.csv", result.filename());
    assertEquals(new MediaType("text", "csv"), result.contentType());
    assertEquals(SIGNED_URL, result.signedUrl());
    verify(downloadUrlGateway)
        .createSignedUrl(
            "exports/test.csv",
            "layer-export.csv",
            new MediaType("text", "csv"),
            ARTIFACT_EXPIRES_AT);
  }

  @Test
  void download_whenCreatorDiffers_returnsForbiddenBeforeBlobAccess() {
    when(artifactRepository.findById(EXPORT_ID))
        .thenReturn(Optional.of(activeArtifact(UUID.randomUUID())));

    var exception =
        assertThrows(
            GlobalOpenCopException.class, () -> usecase.download(principal(USER_ID), EXPORT_ID));

    assertEquals(HttpStatus.FORBIDDEN, exception.getHttpStatus());
    verifyNoInteractions(downloadUrlGateway);
  }

  @Test
  void download_whenExportIsExpired_returnsGoneBeforeBlobAccess() {
    when(artifactRepository.findById(EXPORT_ID)).thenReturn(Optional.of(expiredArtifact(USER_ID)));

    var exception =
        assertThrows(
            GlobalOpenCopException.class, () -> usecase.download(principal(USER_ID), EXPORT_ID));

    assertEquals(HttpStatus.GONE, exception.getHttpStatus());
    verifyNoInteractions(downloadUrlGateway);
  }

  private static ExportArtifact activeArtifact(UUID creatorId) {
    return artifact(creatorId, ARTIFACT_EXPIRES_AT);
  }

  private static ExportArtifact expiredArtifact(UUID creatorId) {
    return artifact(creatorId, OffsetDateTime.ofInstant(NOW.minusSeconds(60), ZoneOffset.UTC));
  }

  private static ExportArtifact artifact(UUID creatorId, OffsetDateTime expiresAt) {
    return new ExportArtifact(
        EXPORT_ID,
        "LAYER",
        SUBJECT_ID,
        creatorId,
        "CSV",
        "exports/test.csv",
        "layer-export.csv",
        OffsetDateTime.ofInstant(NOW.minusSeconds(60), ZoneOffset.UTC),
        expiresAt);
  }

  private static OpenCopPrincipal principal(UUID userId) {
    return (OpenCopPrincipal) TestAuthentications.oidcUser(userId).getPrincipal();
  }
}
