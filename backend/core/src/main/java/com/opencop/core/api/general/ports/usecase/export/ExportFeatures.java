// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.usecase.export;

import com.opencop.core.api.general.technical.model.export.ExportRequest;
import com.opencop.core.api.general.technical.model.export.ExportResponse;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;

public interface ExportFeatures {

  ExportResponse create(OpenCopPrincipal principal, ExportRequest request);
}
