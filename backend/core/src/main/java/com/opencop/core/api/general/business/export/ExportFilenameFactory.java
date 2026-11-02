// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.export;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ExportFilenameFactory {

  private static final DateTimeFormatter TIMESTAMP_FORMAT =
      DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssX");
  private static final int EXPORT_ID_SUFFIX_LENGTH = 8;
  private static final String SELECTION_SLUG = "selection";

  public String createSelection(UUID exportId, OffsetDateTime generatedAt, String fileExtension) {
    return TIMESTAMP_FORMAT.format(generatedAt.toInstant().atOffset(ZoneOffset.UTC))
        + "-"
        + SELECTION_SLUG
        + "-"
        + exportId.toString().substring(0, EXPORT_ID_SUFFIX_LENGTH)
        + fileExtension;
  }
}
