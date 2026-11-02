// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.export;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExportFilenameFactoryTest {

  private static final UUID EXPORT_ID = UUID.fromString("1202f07a-e75c-4c7b-99db-fa707b3b07e4");
  private static final OffsetDateTime GENERATED_AT = OffsetDateTime.parse("2026-06-09T19:42:25Z");

  private final ExportFilenameFactory factory = new ExportFilenameFactory();

  @Test
  void createSelection_putsUtcTimestampFirstForFileExplorerSorting() {
    var filename = factory.createSelection(EXPORT_ID, GENERATED_AT, ".csv");

    assertEquals("20260609T194225Z-selection-1202f07a.csv", filename);
  }

  @Test
  void createSelection_normalizesTimestampToUtc() {
    var filename =
        factory.createSelection(
            EXPORT_ID, OffsetDateTime.parse("2026-06-09T15:42:25-04:00"), ".csv");

    assertEquals("20260609T194225Z-selection-1202f07a.csv", filename);
  }

  @Test
  void createSelection_truncatesExportIdToFirstEightChars() {
    var filename =
        factory.createSelection(
            UUID.fromString("abcdef01-2345-6789-abcd-ef0123456789"), GENERATED_AT, ".csv");

    assertEquals("20260609T194225Z-selection-abcdef01.csv", filename);
  }
}
