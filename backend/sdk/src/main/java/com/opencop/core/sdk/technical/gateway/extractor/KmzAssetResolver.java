// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.extractor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves KML icon href references against extracted ZIP assets. Runs in Core at import time,
 * where we have ground-truth access to both the KML content and the extracted image entries.
 */
public final class KmzAssetResolver {

  private static final Logger log = LoggerFactory.getLogger(KmzAssetResolver.class);

  // Regex-based href extraction — not ideal. This exists because Core lacks the Saxon XPath
  // infrastructure the Processor has (KmlStyleIndex). The thin-envelope approach described in
  // LayerDataImporterUsecase would eliminate this class entirely in favor of proper XML parsing.
  // See the FUTURE ARCHITECTURE IMPROVEMENT comment there for the full explanation.
  private static final Pattern HREF_PATTERN =
      Pattern.compile("<href>\\s*(.*?)\\s*</href>", Pattern.DOTALL);

  /**
   * Resolves icon href references found in KML entries of a stripped ZIP against extracted assets.
   *
   * @param strippedZip the ZIP with images removed but KML entries still present
   * @param assetPathToMediaInfo mapping of normalized ZIP entry paths to media info (ID + dims)
   * @return list of icon references with resolution status
   */
  /** Metadata about an extracted media asset, used to populate KmzIconRef with dimensions. */
  public record MediaInfo(UUID mediaId, Integer widthPx, Integer heightPx) {}

  public static List<KmzIconRef> resolve(
      byte[] strippedZip, Map<String, MediaInfo> assetPathToMediaInfo) throws IOException {

    List<KmzIconRef> refs = new ArrayList<>();
    Set<String> seen = new LinkedHashSet<>();

    try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(strippedZip))) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        if (entry.isDirectory()) continue;

        String entryName = entry.getName();
        if (!isKmlEntry(entryName)) continue;

        String kmlPath = ArchivePaths.normalize(entryName);
        if (kmlPath == null) continue;

        byte[] kmlBytes = zis.readAllBytes();
        String kmlContent = new String(kmlBytes, StandardCharsets.UTF_8);

        Matcher matcher = HREF_PATTERN.matcher(kmlContent);
        while (matcher.find()) {
          String rawHref = matcher.group(1).trim();
          if (rawHref.isEmpty()) continue;

          // Deduplicate within this resolution pass
          String dedupeKey = kmlPath + "\0" + rawHref;
          if (!seen.add(dedupeKey)) continue;

          refs.add(resolveOneHref(kmlPath, rawHref, assetPathToMediaInfo));
        }
      }
    }

    return refs;
  }

  private static KmzIconRef resolveOneHref(
      String kmlPath, String rawHref, Map<String, MediaInfo> assetPathToMediaInfo) {

    // External URLs — not archive-relative
    if (rawHref.startsWith("http://")
        || rawHref.startsWith("https://")
        || rawHref.startsWith("data:")) {
      return new KmzIconRef(kmlPath, rawHref, null, null, KmzIconRef.Status.EXTERNAL, null, null);
    }

    String resolved = ArchivePaths.resolve(kmlPath, rawHref);
    if (resolved == null) {
      log.warn(
          "KMZ icon ref could not be resolved: kml={}, href={}, available={}",
          kmlPath,
          rawHref,
          assetPathToMediaInfo.keySet());
      return new KmzIconRef(kmlPath, rawHref, null, null, KmzIconRef.Status.UNRESOLVED, null, null);
    }

    MediaInfo info = assetPathToMediaInfo.get(resolved);
    if (info == null) {
      // Resolved to a path but no matching extracted asset (e.g., non-image file, or missing)
      log.warn(
          "KMZ icon ref resolved to path with no media: kml={}, href={}, resolved={}, available={}",
          kmlPath,
          rawHref,
          resolved,
          assetPathToMediaInfo.keySet());
      return new KmzIconRef(
          kmlPath, rawHref, resolved, null, KmzIconRef.Status.UNRESOLVED, null, null);
    }

    return new KmzIconRef(
        kmlPath,
        rawHref,
        resolved,
        info.mediaId(),
        KmzIconRef.Status.RESOLVED,
        info.widthPx(),
        info.heightPx());
  }

  private static boolean isKmlEntry(String name) {
    return name.toLowerCase(java.util.Locale.ROOT).endsWith(".kml");
  }

  private KmzAssetResolver() {}
}
