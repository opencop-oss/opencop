// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.extractor;

import static org.junit.jupiter.api.Assertions.*;

import com.opencop.core.sdk.technical.gateway.extractor.KmzAssetResolver.MediaInfo;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests the full KMZ icon resolution pipeline: ZipSiphon extracts images → build pathToMediaInfo
 * map → KmzAssetResolver resolves KML hrefs against the map → manifest lookup produces the symbol
 * value FeatureBuilder would use.
 *
 * <p>These tests build KMZ byte arrays in memory to exercise the resolution logic with concrete
 * data.
 */
class KmzAssetResolverTest {

  private static MediaInfo info(UUID mediaId) {
    return new MediaInfo(mediaId, null, null);
  }

  // -- Full pipeline tests: KMZ → siphon → resolve → manifest lookup → symbol value --

  @Nested
  class FullPipeline {

    @Test
    void resolves_multiple_project_authored_local_icons() throws IOException {
      byte[] kmz =
          kmzBuilder()
              .addKml(
                  "doc.kml",
                  """
                  <kml xmlns="http://www.opengis.net/kml/2.2">
                    <Document>
                      <Style id="primary-marker">
                        <IconStyle><Icon><href>icons/primary-marker.png</href></Icon></IconStyle>
                      </Style>
                      <Style id="secondary-marker">
                        <IconStyle><Icon><href>icons/secondary-marker.png</href></Icon></IconStyle>
                      </Style>
                      <Placemark>
                        <styleUrl>#primary-marker</styleUrl>
                        <Point><coordinates>-100.25,40.25,0</coordinates></Point>
                      </Placemark>
                    </Document>
                  </kml>
                  """)
              .addImage("icons/primary-marker.png")
              .addImage("icons/secondary-marker.png")
              .build();

      // Step 1: ZipSiphon extracts images (same as today)
      ZipSiphon.Result siphoned = ZipSiphon.siphon(kmz, ZipSiphon.imagesOnly(), 100, 10_000_000);

      assertEquals(2, siphoned.extractedByPath().size());

      // Step 2: Core creates media records (simulated)
      UUID primaryMediaId = UUID.randomUUID();
      UUID secondaryMediaId = UUID.randomUUID();
      Map<String, MediaInfo> pathToMediaInfo = new HashMap<>();
      pathToMediaInfo.put("icons/primary-marker.png", info(primaryMediaId));
      pathToMediaInfo.put("icons/secondary-marker.png", info(secondaryMediaId));

      // Step 3: KmzAssetResolver builds manifest from stripped ZIP
      List<KmzIconRef> manifest = KmzAssetResolver.resolve(siphoned.strippedZip(), pathToMediaInfo);

      assertEquals(2, manifest.size());

      // Step 4: Processor extracts iconHref from KML (KmlStyleIndex would return these)
      // and looks up the manifest to get the symbol value for FeatureBuilder
      KmzIconRef primaryRef = KmzIconRef.lookup(manifest, "doc.kml", "icons/primary-marker.png");
      assertNotNull(primaryRef);
      assertEquals(KmzIconRef.Status.RESOLVED, primaryRef.status());
      assertEquals("opencop:media:" + primaryMediaId, primaryRef.toSymbolValue());

      KmzIconRef secondaryRef =
          KmzIconRef.lookup(manifest, "doc.kml", "icons/secondary-marker.png");
      assertNotNull(secondaryRef);
      assertEquals(KmzIconRef.Status.RESOLVED, secondaryRef.status());
      assertEquals("opencop:media:" + secondaryMediaId, secondaryRef.toSymbolValue());
    }

    @Test
    void missing_icon_file_resolves_to_null_symbol() throws IOException {
      // KML references icons/marker.png but that file is NOT in the KMZ
      byte[] kmz =
          kmzBuilder()
              .addKml(
                  "doc.kml",
                  """
                  <kml xmlns="http://www.opengis.net/kml/2.2">
                    <Document>
                      <Style id="s1">
                        <IconStyle><Icon><href>icons/marker.png</href></Icon></IconStyle>
                      </Style>
                    </Document>
                  </kml>
                  """)
              // No image added — the icon file is missing
              .build();

      ZipSiphon.Result siphoned = ZipSiphon.siphon(kmz, ZipSiphon.imagesOnly(), 100, 10_000_000);
      assertTrue(siphoned.extractedByPath().isEmpty());

      List<KmzIconRef> manifest = KmzAssetResolver.resolve(siphoned.strippedZip(), Map.of());

      assertEquals(1, manifest.size());
      KmzIconRef ref = manifest.getFirst();
      assertEquals(KmzIconRef.Status.UNRESOLVED, ref.status());
      assertEquals("icons/marker.png", ref.resolvedPath());

      // Processor sees UNRESOLVED → toSymbolValue() returns null
      // FeatureBuilder would need to decide: skip icon, use fallback, or log warning
      assertNull(ref.toSymbolValue());

      // Manifest lookup still works — processor can inspect the status
      KmzIconRef looked = KmzIconRef.lookup(manifest, "doc.kml", "icons/marker.png");
      assertNotNull(looked);
      assertEquals(KmzIconRef.Status.UNRESOLVED, looked.status());
    }

    @Test
    void orphaned_icon_not_referenced_by_kml() throws IOException {
      // KMZ has an image that no KML style references — it gets extracted and uploaded
      // but does NOT appear in the manifest (manifest only tracks KML hrefs)
      byte[] kmz =
          kmzBuilder()
              .addKml(
                  "doc.kml",
                  """
                  <kml xmlns="http://www.opengis.net/kml/2.2">
                    <Document>
                      <Style id="s1">
                        <IconStyle><Icon><href>icons/used.png</href></Icon></IconStyle>
                      </Style>
                    </Document>
                  </kml>
                  """)
              .addImage("icons/used.png")
              .addImage("icons/orphaned.png") // not referenced by any KML
              .build();

      ZipSiphon.Result siphoned = ZipSiphon.siphon(kmz, ZipSiphon.imagesOnly(), 100, 10_000_000);

      // ZipSiphon extracts both images (it doesn't know about KML)
      assertEquals(2, siphoned.extractedByPath().size());

      // Core creates media records for both (it doesn't know which are referenced)
      UUID usedMediaId = UUID.randomUUID();
      UUID orphanedMediaId = UUID.randomUUID();
      Map<String, MediaInfo> pathToMediaInfo = new HashMap<>();
      pathToMediaInfo.put("icons/used.png", info(usedMediaId));
      pathToMediaInfo.put("icons/orphaned.png", info(orphanedMediaId));

      // Manifest only contains the href that appears in the KML
      List<KmzIconRef> manifest = KmzAssetResolver.resolve(siphoned.strippedZip(), pathToMediaInfo);

      assertEquals(1, manifest.size());
      assertEquals("icons/used.png", manifest.getFirst().rawHref());
      assertEquals(KmzIconRef.Status.RESOLVED, manifest.getFirst().status());
      // orphaned.png has a media record but is not in the manifest — harmless orphan
    }

    @Test
    void external_url_passes_through_as_symbol() throws IOException {
      // KML references an external HTTP URL — not an archive-relative icon
      byte[] kmz =
          kmzBuilder()
              .addKml(
                  "doc.kml",
                  """
                  <kml xmlns="http://www.opengis.net/kml/2.2">
                    <Document>
                      <Style id="s1">
                        <IconStyle><Icon><href>https://example.com/icon.png</href></Icon></IconStyle>
                      </Style>
                    </Document>
                  </kml>
                  """)
              .build();

      ZipSiphon.Result siphoned = ZipSiphon.siphon(kmz, ZipSiphon.imagesOnly(), 100, 10_000_000);
      List<KmzIconRef> manifest = KmzAssetResolver.resolve(siphoned.strippedZip(), Map.of());

      assertEquals(1, manifest.size());
      KmzIconRef ref = manifest.getFirst();
      assertEquals(KmzIconRef.Status.EXTERNAL, ref.status());

      // External URLs pass through as the symbol value (same as today's http passthrough)
      assertEquals("https://example.com/icon.png", ref.toSymbolValue());
    }

    @Test
    void mixed_resolved_external_and_missing() throws IOException {
      // Realistic KMZ: some icons resolve, one is external, one is missing
      byte[] kmz =
          kmzBuilder()
              .addKml(
                  "doc.kml",
                  """
                  <kml xmlns="http://www.opengis.net/kml/2.2">
                    <Document>
                      <Style id="local">
                        <IconStyle><Icon><href>icons/local.png</href></Icon></IconStyle>
                      </Style>
                      <Style id="remote">
                        <IconStyle><Icon><href>https://cdn.example.com/marker.png</href></Icon></IconStyle>
                      </Style>
                      <Style id="broken">
                        <IconStyle><Icon><href>icons/doesnt-exist.png</href></Icon></IconStyle>
                      </Style>
                    </Document>
                  </kml>
                  """)
              .addImage("icons/local.png")
              .build();

      ZipSiphon.Result siphoned = ZipSiphon.siphon(kmz, ZipSiphon.imagesOnly(), 100, 10_000_000);

      UUID localMediaId = UUID.randomUUID();
      Map<String, MediaInfo> pathToMediaInfo = Map.of("icons/local.png", info(localMediaId));

      List<KmzIconRef> manifest = KmzAssetResolver.resolve(siphoned.strippedZip(), pathToMediaInfo);

      assertEquals(3, manifest.size());

      // Processor looks up each iconHref extracted by KmlStyleIndex
      KmzIconRef local = KmzIconRef.lookup(manifest, "doc.kml", "icons/local.png");
      assertEquals("opencop:media:" + localMediaId, local.toSymbolValue());

      KmzIconRef remote =
          KmzIconRef.lookup(manifest, "doc.kml", "https://cdn.example.com/marker.png");
      assertEquals("https://cdn.example.com/marker.png", remote.toSymbolValue());

      KmzIconRef broken = KmzIconRef.lookup(manifest, "doc.kml", "icons/doesnt-exist.png");
      assertNull(broken.toSymbolValue());
    }

    @Test
    void manifest_lookup_returns_null_for_unknown_href() throws IOException {
      // Processor gets an iconHref that's not in the manifest at all
      // (shouldn't happen if Core and Processor parse KML the same way, but defensive)
      byte[] kmz =
          kmzBuilder()
              .addKml(
                  "doc.kml",
                  """
                  <kml xmlns="http://www.opengis.net/kml/2.2">
                    <Document>
                      <Style id="s1">
                        <IconStyle><Icon><href>icons/marker.png</href></Icon></IconStyle>
                      </Style>
                    </Document>
                  </kml>
                  """)
              .addImage("icons/marker.png")
              .build();

      ZipSiphon.Result siphoned = ZipSiphon.siphon(kmz, ZipSiphon.imagesOnly(), 100, 10_000_000);
      UUID mediaId = UUID.randomUUID();
      List<KmzIconRef> manifest =
          KmzAssetResolver.resolve(
              siphoned.strippedZip(), Map.of("icons/marker.png", info(mediaId)));

      // Processor somehow extracts a different href — lookup misses
      KmzIconRef result = KmzIconRef.lookup(manifest, "doc.kml", "icons/MARKER.png");
      assertNull(result); // case mismatch — not found
    }
  }

  // -- Path normalization edge cases --

  @Nested
  class PathNormalization {

    @Test
    void dot_slash_prefix_resolves_to_same_media() throws IOException {
      byte[] kmz =
          kmzBuilder()
              .addKml(
                  "doc.kml",
                  """
                  <kml xmlns="http://www.opengis.net/kml/2.2">
                    <Document>
                      <Style id="s1">
                        <IconStyle><Icon><href>./icons/marker.png</href></Icon></IconStyle>
                      </Style>
                    </Document>
                  </kml>
                  """)
              .addImage("icons/marker.png")
              .build();

      ZipSiphon.Result siphoned = ZipSiphon.siphon(kmz, ZipSiphon.imagesOnly(), 100, 10_000_000);
      UUID mediaId = UUID.randomUUID();

      List<KmzIconRef> manifest =
          KmzAssetResolver.resolve(
              siphoned.strippedZip(), Map.of("icons/marker.png", info(mediaId)));

      // Processor would extract raw href "./icons/marker.png" from KML
      // Manifest stores it as the raw href, so lookup must use the raw href
      KmzIconRef ref = KmzIconRef.lookup(manifest, "doc.kml", "./icons/marker.png");
      assertNotNull(ref);
      assertEquals("opencop:media:" + mediaId, ref.toSymbolValue());
      // The resolved path is the normalized form
      assertEquals("icons/marker.png", ref.resolvedPath());
    }

    @Test
    void nested_kml_with_parent_traversal() throws IOException {
      byte[] kmz =
          kmzBuilder()
              .addKml(
                  "sub/doc.kml",
                  """
                  <kml xmlns="http://www.opengis.net/kml/2.2">
                    <Document>
                      <Style id="s1">
                        <IconStyle><Icon><href>../icons/marker.png</href></Icon></IconStyle>
                      </Style>
                    </Document>
                  </kml>
                  """)
              .addImage("icons/marker.png")
              .build();

      ZipSiphon.Result siphoned = ZipSiphon.siphon(kmz, ZipSiphon.imagesOnly(), 100, 10_000_000);
      UUID mediaId = UUID.randomUUID();

      List<KmzIconRef> manifest =
          KmzAssetResolver.resolve(
              siphoned.strippedZip(), Map.of("icons/marker.png", info(mediaId)));

      KmzIconRef ref = KmzIconRef.lookup(manifest, "sub/doc.kml", "../icons/marker.png");
      assertNotNull(ref);
      assertEquals(KmzIconRef.Status.RESOLVED, ref.status());
      assertEquals("icons/marker.png", ref.resolvedPath());
      assertEquals("opencop:media:" + mediaId, ref.toSymbolValue());
    }

    @Test
    void backslash_separator_resolves() throws IOException {
      byte[] kmz =
          kmzBuilder()
              .addKml(
                  "doc.kml",
                  """
                  <kml xmlns="http://www.opengis.net/kml/2.2">
                    <Document>
                      <Style id="s1">
                        <IconStyle><Icon><href>icons\\marker.png</href></Icon></IconStyle>
                      </Style>
                    </Document>
                  </kml>
                  """)
              .addImage("icons/marker.png")
              .build();

      ZipSiphon.Result siphoned = ZipSiphon.siphon(kmz, ZipSiphon.imagesOnly(), 100, 10_000_000);
      UUID mediaId = UUID.randomUUID();

      List<KmzIconRef> manifest =
          KmzAssetResolver.resolve(
              siphoned.strippedZip(), Map.of("icons/marker.png", info(mediaId)));

      KmzIconRef ref = KmzIconRef.lookup(manifest, "doc.kml", "icons\\marker.png");
      assertNotNull(ref);
      assertEquals(KmzIconRef.Status.RESOLVED, ref.status());
    }

    @Test
    void root_escape_attempt_is_unresolved() throws IOException {
      byte[] kmz =
          kmzBuilder()
              .addKml(
                  "doc.kml",
                  """
                  <kml xmlns="http://www.opengis.net/kml/2.2">
                    <Document>
                      <Style id="s1">
                        <IconStyle><Icon><href>../../etc/passwd</href></Icon></IconStyle>
                      </Style>
                    </Document>
                  </kml>
                  """)
              .build();

      ZipSiphon.Result siphoned = ZipSiphon.siphon(kmz, ZipSiphon.imagesOnly(), 100, 10_000_000);
      List<KmzIconRef> manifest = KmzAssetResolver.resolve(siphoned.strippedZip(), Map.of());

      KmzIconRef ref = manifest.getFirst();
      assertEquals(KmzIconRef.Status.UNRESOLVED, ref.status());
      assertNull(ref.toSymbolValue());
    }

    @Test
    void deduplicates_same_href_across_multiple_styles() throws IOException {
      byte[] kmz =
          kmzBuilder()
              .addKml(
                  "doc.kml",
                  """
                  <kml xmlns="http://www.opengis.net/kml/2.2">
                    <Document>
                      <Style id="s1"><IconStyle><Icon><href>icons/marker.png</href></Icon></IconStyle></Style>
                      <Style id="s2"><IconStyle><Icon><href>icons/marker.png</href></Icon></IconStyle></Style>
                      <Style id="s3"><IconStyle><Icon><href>icons/marker.png</href></Icon></IconStyle></Style>
                    </Document>
                  </kml>
                  """)
              .addImage("icons/marker.png")
              .build();

      ZipSiphon.Result siphoned = ZipSiphon.siphon(kmz, ZipSiphon.imagesOnly(), 100, 10_000_000);
      UUID mediaId = UUID.randomUUID();
      List<KmzIconRef> manifest =
          KmzAssetResolver.resolve(
              siphoned.strippedZip(), Map.of("icons/marker.png", info(mediaId)));

      // One manifest entry, not three
      assertEquals(1, manifest.size());
      assertEquals("opencop:media:" + mediaId, manifest.getFirst().toSymbolValue());
    }

    @Test
    void double_dots_in_filename_not_rejected() throws IOException {
      byte[] kmz =
          kmzBuilder()
              .addKml(
                  "doc.kml",
                  """
                  <kml xmlns="http://www.opengis.net/kml/2.2">
                    <Document>
                      <Style id="s1">
                        <IconStyle><Icon><href>icon..png</href></Icon></IconStyle>
                      </Style>
                    </Document>
                  </kml>
                  """)
              .addImage("icon..png")
              .build();

      ZipSiphon.Result siphoned = ZipSiphon.siphon(kmz, ZipSiphon.imagesOnly(), 100, 10_000_000);
      UUID mediaId = UUID.randomUUID();

      List<KmzIconRef> manifest =
          KmzAssetResolver.resolve(siphoned.strippedZip(), Map.of("icon..png", info(mediaId)));

      assertEquals(1, manifest.size());
      assertEquals(KmzIconRef.Status.RESOLVED, manifest.getFirst().status());
    }
  }

  // -- Helpers --

  private static KmzBuilder kmzBuilder() {
    return new KmzBuilder();
  }

  /** Builds a KMZ (ZIP) byte array in-memory for testing. */
  private static class KmzBuilder {
    private final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    private final ZipOutputStream zos = new ZipOutputStream(baos);

    KmzBuilder addKml(String path, String content) throws IOException {
      zos.putNextEntry(new ZipEntry(path));
      zos.write(content.getBytes(StandardCharsets.UTF_8));
      zos.closeEntry();
      return this;
    }

    KmzBuilder addImage(String path) throws IOException {
      // Dummy PNG-like bytes — ZipSiphon checks extension, not magic bytes
      zos.putNextEntry(new ZipEntry(path));
      zos.write(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0, 0, 0, 0});
      zos.closeEntry();
      return this;
    }

    byte[] build() throws IOException {
      zos.close();
      return baos.toByteArray();
    }
  }
}
