// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MediaBlobKeyTest {

  private static final UUID MEDIA_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");

  @Test
  void generate_producesExpectedFormat() {
    var date = LocalDate.of(2026, 3, 12);
    var key = MediaBlobKey.generate(MEDIA_ID, "icon.png", date);
    assertEquals("media/2026/03/12/a1b2c3d4-e5f6-7890-abcd-ef1234567890/icon.png", key);
  }

  @Test
  void generate_singleDigitMonthAndDay_zeroPadded() {
    var date = LocalDate.of(2026, 1, 5);
    var key = MediaBlobKey.generate(MEDIA_ID, "photo.jpg", date);
    assertEquals("media/2026/01/05/a1b2c3d4-e5f6-7890-abcd-ef1234567890/photo.jpg", key);
  }

  @Test
  void generate_slugifiesFilename() {
    var date = LocalDate.of(2026, 3, 12);
    var key = MediaBlobKey.generate(MEDIA_ID, "My Photo (1).PNG", date);
    assertEquals("media/2026/03/12/a1b2c3d4-e5f6-7890-abcd-ef1234567890/my-photo-1.png", key);
  }

  @Test
  void generate_nullFilename_usesUploadFallback() {
    var date = LocalDate.of(2026, 3, 12);
    var key = MediaBlobKey.generate(MEDIA_ID, null, date);
    assertEquals("media/2026/03/12/a1b2c3d4-e5f6-7890-abcd-ef1234567890/unknown", key);
  }

  @Test
  void generate_emptySlugWithValidExtension_usesUploadWithExtension() {
    var date = LocalDate.of(2026, 3, 12);
    var key = MediaBlobKey.generate(MEDIA_ID, ".png", date);
    assertEquals("media/2026/03/12/a1b2c3d4-e5f6-7890-abcd-ef1234567890/unknown.png", key);
  }

  @Test
  void generate_emptySlugNoExtension_usesUpload() {
    var date = LocalDate.of(2026, 3, 12);
    var key = MediaBlobKey.generate(MEDIA_ID, "!!!", date);
    assertEquals("media/2026/03/12/a1b2c3d4-e5f6-7890-abcd-ef1234567890/unknown", key);
  }

  @Test
  void generate_noDateOverload_usesToday() {
    var key = MediaBlobKey.generate(MEDIA_ID, "icon.png");
    // Can't assert exact date, but verify the format
    assertTrue(key.matches("media/\\d{4}/\\d{2}/\\d{2}/" + MEDIA_ID + "/icon\\.png"));
  }
}
