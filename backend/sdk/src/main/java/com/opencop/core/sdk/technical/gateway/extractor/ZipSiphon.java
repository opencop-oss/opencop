// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.extractor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serial;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class ZipSiphon {

  private static final ArchiveLimits UNBOUNDED_ARCHIVE =
      new ArchiveLimits(Integer.MAX_VALUE, Long.MAX_VALUE);

  public interface EntrySelector {
    boolean shouldExtract(String normalizedPath);
  }

  public record ZipAsset(String path, byte[] bytes, String contentType) {}

  public record ArchiveLimits(int maxEntries, long maxTotalUncompressedBytes) {

    public ArchiveLimits {
      if (maxEntries < 1) {
        throw new IllegalArgumentException("maxEntries must be positive");
      }
      if (maxTotalUncompressedBytes < 1) {
        throw new IllegalArgumentException("maxTotalUncompressedBytes must be positive");
      }
    }
  }

  public static final class ZipLimitExceededException extends IOException {

    @Serial private static final long serialVersionUID = 1L;

    private ZipLimitExceededException(String message) {
      super(message);
    }
  }

  public static final class ZipEntryReadException extends IOException {

    @Serial private static final long serialVersionUID = 1L;

    private final String entryPath;

    private ZipEntryReadException(String entryPath, IOException cause) {
      super("Failed to read ZIP entry", cause);
      this.entryPath = entryPath;
    }

    public String entryPath() {
      return entryPath;
    }
  }

  public record Result(byte[] strippedZip, Map<String, ZipAsset> extractedByPath) {
    public Map<String, byte[]> extractedBytesByPath() {
      Map<String, byte[]> m = new HashMap<>();
      extractedByPath.forEach((k, v) -> m.put(k, v.bytes()));
      return m;
    }
  }

  public static boolean isZip(byte[] bytes) {
    if (bytes == null || bytes.length < 4) {
      return false;
    }
    return bytes[0] == 'P'
        && bytes[1] == 'K'
        && (bytes[2] == 3 || bytes[2] == 5 || bytes[2] == 7)
        && (bytes[3] == 4 || bytes[3] == 6 || bytes[3] == 8);
  }

  public static Result siphon(
      byte[] zipBytes, EntrySelector selector, int maxExtractedFiles, long maxTotalExtractedBytes)
      throws IOException {
    return siphon(zipBytes, selector, maxExtractedFiles, maxTotalExtractedBytes, UNBOUNDED_ARCHIVE);
  }

  public static Result siphon(
      byte[] zipBytes,
      EntrySelector selector,
      int maxExtractedFiles,
      long maxTotalExtractedBytes,
      ArchiveLimits archiveLimits)
      throws IOException {

    if (!isZip(zipBytes)) {
      return new Result(zipBytes, Map.of());
    }

    Map<String, ZipAsset> extracted = new LinkedHashMap<>();
    long extractedBytes = 0;
    int extractedFiles = 0;
    long totalUncompressedBytes = 0;
    int totalEntries = 0;

    ByteArrayOutputStream strippedOut = new ByteArrayOutputStream();
    try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes));
        ZipOutputStream zos = new ZipOutputStream(strippedOut)) {

      ZipEntry in;
      while ((in = zis.getNextEntry()) != null) {
        totalEntries++;
        if (totalEntries > archiveLimits.maxEntries()) {
          throw new ZipLimitExceededException("ZIP entry count exceeded budget");
        }

        if (in.isDirectory()) {
          totalUncompressedBytes +=
              discardWithBudget(
                  zis, archiveLimits.maxTotalUncompressedBytes() - totalUncompressedBytes, null);
          continue;
        }

        String normalized = normalizeEntryName(in.getName());
        if (normalized == null) {
          totalUncompressedBytes +=
              discardWithBudget(
                  zis, archiveLimits.maxTotalUncompressedBytes() - totalUncompressedBytes, null);
          continue;
        }

        if (selector.shouldExtract(normalized)) {
          if (extractedFiles >= maxExtractedFiles) {
            throw new ZipLimitExceededException(
                "Too many extracted files (max=" + maxExtractedFiles + ")");
          }
          byte[] data =
              readAllBytesWithBudget(
                  zis,
                  Math.min(
                      maxTotalExtractedBytes - extractedBytes,
                      archiveLimits.maxTotalUncompressedBytes() - totalUncompressedBytes),
                  normalized);

          extractedBytes += data.length;
          totalUncompressedBytes += data.length;
          extractedFiles++;

          extracted.put(normalized, new ZipAsset(normalized, data, guessContentType(normalized)));
          continue; // not copied to output zip
        }

        // keep entry in stripped zip
        ZipEntry out = new ZipEntry(normalized);
        if (in.getTime() != -1) out.setTime(in.getTime());

        zos.putNextEntry(out);
        totalUncompressedBytes +=
            copyWithBudget(
                zis,
                zos,
                archiveLimits.maxTotalUncompressedBytes() - totalUncompressedBytes,
                normalized);
        zos.closeEntry();
      }
    }

    return new Result(strippedOut.toByteArray(), extracted);
  }

  // Convenience: default “images only” selector
  public static EntrySelector imagesOnly() {
    return path -> {
      String p = path.toLowerCase(Locale.ROOT);
      int dot = p.lastIndexOf('.');
      if (dot < 0) {
        return false;
      }
      String ext = p.substring(dot + 1);
      return switch (ext) {
        case "png", "jpg", "jpeg", "gif", "bmp", "webp", "svg" -> true;
        default -> false;
      };
    };
  }

  private static String normalizeEntryName(String raw) {
    if (raw == null) {
      return null;
    }
    String s = raw.replace('\\', '/').trim();
    while (s.startsWith("/")) s = s.substring(1);
    s = s.replaceAll("/{2,}", "/");
    if (s.isBlank() || s.contains("..")) {
      return null;
    }
    return s;
  }

  private static byte[] readAllBytesWithBudget(InputStream in, long budget, String entryPath)
      throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buf = new byte[8192];
    long read = 0;
    try {
      int r;
      while ((r = in.read(buf)) != -1) {
        if (budget < 0 || r > budget - read) {
          throw new ZipLimitExceededException("ZIP content exceeded byte budget");
        }
        read += r;
        out.write(buf, 0, r);
      }
    } catch (ZipLimitExceededException exception) {
      throw exception;
    } catch (IOException exception) {
      throw new ZipEntryReadException(entryPath, exception);
    }
    return out.toByteArray();
  }

  private static long copyWithBudget(
      InputStream in, OutputStream out, long budget, String entryPath) throws IOException {
    byte[] buf = new byte[8192];
    long read = 0;
    try {
      int r;
      while ((r = in.read(buf)) != -1) {
        if (budget < 0 || r > budget - read) {
          throw new ZipLimitExceededException("ZIP content exceeded byte budget");
        }
        read += r;
        out.write(buf, 0, r);
      }
    } catch (ZipLimitExceededException exception) {
      throw exception;
    } catch (IOException exception) {
      throw new ZipEntryReadException(entryPath, exception);
    }
    return read;
  }

  private static long discardWithBudget(InputStream in, long budget, String entryPath)
      throws IOException {
    return copyWithBudget(in, OutputStream.nullOutputStream(), budget, entryPath);
  }

  private static String guessContentType(String path) {
    String p = path.toLowerCase(Locale.ROOT);
    if (p.endsWith(".png")) return "image/png";
    if (p.endsWith(".jpg") || p.endsWith(".jpeg")) return "image/jpeg";
    if (p.endsWith(".gif")) return "image/gif";
    if (p.endsWith(".bmp")) return "image/bmp";
    if (p.endsWith(".webp")) return "image/webp";
    if (p.endsWith(".svg")) return "image/svg+xml";
    return "application/octet-stream";
  }

  private ZipSiphon() {}
}
