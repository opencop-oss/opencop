// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.usecase.export;

import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.UUID;
import org.springframework.http.MediaType;

public interface DownloadExport {

  DownloadRedirect download(OpenCopPrincipal principal, UUID exportId);

  record DownloadRedirect(String filename, MediaType contentType, String signedUrl) {}
}
