// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.export;

import com.opencop.core.api.general.technical.model.export.ExportRequest;
import java.util.List;
import org.springframework.http.MediaType;

public interface FeatureExportMapper {

  byte[] map(List<ActiveFeatureExportRow> rows);

  ExportRequest.Format format();

  String fileExtension();

  MediaType contentType();
}
