// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.gateway.export;

import java.time.OffsetDateTime;
import org.springframework.http.MediaType;

public interface ExportDownloadUrlGateway {

  String createSignedUrl(
      String blobPath, String filename, MediaType contentType, OffsetDateTime maxExpiry);
}
