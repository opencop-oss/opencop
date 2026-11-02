// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.extractor;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class ZipSiphonTest {

  @Test
  void archiveEntryLimitCountsEntriesThatAreNotSelected() throws Exception {
    var entries = new LinkedHashMap<String, byte[]>();
    entries.put("first.bin", new byte[0]);
    entries.put("second.bin", new byte[0]);
    entries.put("third.bin", new byte[0]);

    assertThatThrownBy(
            () ->
                ZipSiphon.siphon(
                    archive(entries), _ -> false, 10, 100, new ZipSiphon.ArchiveLimits(2, 100)))
        .isInstanceOf(ZipSiphon.ZipLimitExceededException.class);
  }

  @Test
  void archiveByteLimitCountsContentThatIsNotSelected() throws Exception {
    assertThatThrownBy(
            () ->
                ZipSiphon.siphon(
                    archive(Map.of("skipped.bin", new byte[11])),
                    _ -> false,
                    10,
                    100,
                    new ZipSiphon.ArchiveLimits(10, 10)))
        .isInstanceOf(ZipSiphon.ZipLimitExceededException.class);
  }

  private static byte[] archive(Map<String, byte[]> entries) throws Exception {
    var output = new ByteArrayOutputStream();
    try (var zip = new ZipOutputStream(output)) {
      for (var entry : entries.entrySet()) {
        zip.putNextEntry(new ZipEntry(entry.getKey()));
        zip.write(entry.getValue());
        zip.closeEntry();
      }
    }
    return output.toByteArray();
  }
}
