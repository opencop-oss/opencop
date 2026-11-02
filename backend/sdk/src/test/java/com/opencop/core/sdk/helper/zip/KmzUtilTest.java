// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.helper.zip;

import static org.junit.jupiter.api.Assertions.*;

import com.opencop.core.sdk.technical.mapper.engine.MapperEngineException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class KmzUtilTest {

  @Test
  void is_zip_returns_true_for_zip_magic_bytes() {
    assertTrue(KmzUtil.isZip(new byte[] {0x50, 0x4B, 0x03, 0x04}));
  }

  @Test
  void is_zip_returns_false_for_null_short_or_non_zip_payloads() {
    assertFalse(KmzUtil.isZip(null));
    assertFalse(KmzUtil.isZip(new byte[] {0x50}));
    assertFalse(KmzUtil.isZip("plain".getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void unwrap_to_kml_if_needed_returns_original_payload_for_non_zip() throws MapperEngineException {
    byte[] payload = "not zipped".getBytes(StandardCharsets.UTF_8);

    List<byte[]> result = KmzUtil.unwrapToKmlIfNeeded(payload);

    assertEquals(1, result.size());
    assertArrayEquals(payload, result.getFirst());
  }

  @Test
  void unwrap_to_kml_if_needed_extracts_kml_files_case_insensitively() throws Exception {
    byte[] firstKml = "<kml>one</kml>".getBytes(StandardCharsets.UTF_8);
    byte[] secondKml = "<kml>two</kml>".getBytes(StandardCharsets.UTF_8);
    byte[] kmz =
        zip(
            entry("doc.kml", firstKml),
            entry("nested/SECOND.KML", secondKml),
            entry("images/icon.png", new byte[] {1, 2, 3}));

    List<byte[]> result = KmzUtil.unwrapToKmlIfNeeded(kmz);

    assertEquals(2, result.size());
    assertArrayEquals(firstKml, result.get(0));
    assertArrayEquals(secondKml, result.get(1));
  }

  @Test
  void unwrap_to_kml_if_needed_throws_when_zip_has_no_kml() throws Exception {
    byte[] kmz = zip(entry("images/icon.png", new byte[] {1, 2, 3}));

    MapperEngineException exception =
        assertThrows(MapperEngineException.class, () -> KmzUtil.unwrapToKmlIfNeeded(kmz));

    assertEquals("KMZ_ERROR", exception.getCode());
    assertEquals("KMZ contains no .kml file", exception.getMessage());
    assertNull(exception.getCause());
  }

  @Test
  void unwrap_to_kml_if_needed_reports_no_kml_for_pk_prefixed_payload_without_entries() {
    byte[] invalidZip = new byte[] {0x50, 0x4B, 0x00, 0x00, 0x00};

    MapperEngineException exception =
        assertThrows(MapperEngineException.class, () -> KmzUtil.unwrapToKmlIfNeeded(invalidZip));

    assertEquals("KMZ_ERROR", exception.getCode());
    assertEquals("KMZ contains no .kml file", exception.getMessage());
    assertNull(exception.getCause());
  }

  private static ZipEntryData entry(String name, byte[] bytes) {
    return new ZipEntryData(name, bytes);
  }

  private static byte[] zip(ZipEntryData... entries) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (ZipOutputStream zipOut = new ZipOutputStream(out)) {
      for (ZipEntryData entry : entries) {
        zipOut.putNextEntry(new ZipEntry(entry.name()));
        zipOut.write(entry.bytes());
        zipOut.closeEntry();
      }
    }
    return out.toByteArray();
  }

  private record ZipEntryData(String name, byte[] bytes) {}
}
