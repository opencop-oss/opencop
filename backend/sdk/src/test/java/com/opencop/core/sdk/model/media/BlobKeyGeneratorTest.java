// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BlobKeyGeneratorTest {

  private static final UUID ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
  private static final LocalDate DATE = LocalDate.of(2026, 3, 12);

  @Test
  void generate_mediaPrefix_producesExpectedFormat() {
    var key = BlobKeyGenerator.generate(BlobKeyPrefix.MEDIA, ID, "icon.png", DATE);
    assertEquals("media/2026/03/12/a1b2c3d4-e5f6-7890-abcd-ef1234567890/icon.png", key);
  }

  @Test
  void generate_importPrefix_producesExpectedFormat() {
    var key = BlobKeyGenerator.generate(BlobKeyPrefix.IMPORT, ID, "data.csv", DATE);
    assertEquals("imports/2026/03/12/a1b2c3d4-e5f6-7890-abcd-ef1234567890/data.csv", key);
  }

  @Test
  void generate_importPrefix_slugifiesFilename() {
    var key = BlobKeyGenerator.generate(BlobKeyPrefix.IMPORT, ID, "My Data (1).CSV", DATE);
    assertEquals("imports/2026/03/12/a1b2c3d4-e5f6-7890-abcd-ef1234567890/my-data-1.csv", key);
  }

  @Test
  void generate_nullFilename_usesUnknownFallback() {
    var key = BlobKeyGenerator.generate(BlobKeyPrefix.IMPORT, ID, null, DATE);
    assertEquals("imports/2026/03/12/a1b2c3d4-e5f6-7890-abcd-ef1234567890/unknown", key);
  }

  @Test
  void generate_emptySlugWithValidExtension_usesUnknownWithExtension() {
    var key = BlobKeyGenerator.generate(BlobKeyPrefix.IMPORT, ID, ".kml", DATE);
    assertEquals("imports/2026/03/12/a1b2c3d4-e5f6-7890-abcd-ef1234567890/unknown.kml", key);
  }

  @Test
  void generate_singleDigitMonthAndDay_zeroPadded() {
    var date = LocalDate.of(2026, 1, 5);
    var key = BlobKeyGenerator.generate(BlobKeyPrefix.IMPORT, ID, "file.csv", date);
    assertEquals("imports/2026/01/05/a1b2c3d4-e5f6-7890-abcd-ef1234567890/file.csv", key);
  }

  @Test
  void generate_noDateOverload_usesToday() {
    var key = BlobKeyGenerator.generate(BlobKeyPrefix.IMPORT, ID, "file.csv");
    assertTrue(key.matches("imports/\\d{4}/\\d{2}/\\d{2}/" + ID + "/file\\.csv"));
  }
}
