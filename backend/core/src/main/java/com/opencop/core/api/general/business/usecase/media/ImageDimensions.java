// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.media;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads image width/height from the file header without decoding the full image. Uses {@link
 * javax.imageio.ImageReader} which only parses header bytes (PNG IHDR, JPEG SOF, WebP VP8, etc.).
 * WebP support requires the TwelveMonkeys imageio-webp library on the classpath.
 */
public final class ImageDimensions {

  private static final Logger log = LoggerFactory.getLogger(ImageDimensions.class);

  private ImageDimensions() {}

  /**
   * Read width/height from an image stream. The stream must support {@link InputStream#mark} (wrap
   * in {@link BufferedInputStream} first if it doesn't). The stream is marked and reset so callers
   * can still read the full content afterward.
   *
   * <p>Important: callers must pass the <em>same</em> stream reference they will later read from.
   * If wrapping is needed, wrap before calling this method and use the wrapper for subsequent
   * reads.
   *
   * @return {width, height} or null if dimensions could not be determined
   */
  public static int[] read(InputStream data) {
    if (!data.markSupported()) {
      throw new IllegalArgumentException(
          "Stream must support mark/reset. Wrap in BufferedInputStream before calling.");
    }
    data.mark(Integer.MAX_VALUE);
    try {
      return readViaImageIO(data);
    } catch (IOException e) {
      log.warn("Could not read image dimensions: {}", e.getMessage());
      return null;
    } finally {
      try {
        data.reset();
      } catch (IOException e) {
        log.warn("Could not reset stream after reading dimensions");
      }
    }
  }

  /**
   * Read width/height from bytes already in memory (e.g., from a ZipSiphon asset). No mark/reset
   * needed since the byte array is not consumed.
   */
  public static int[] read(byte[] data) {
    try (var bais = new ByteArrayInputStream(data)) {
      return readViaImageIO(bais);
    } catch (IOException e) {
      log.warn("Could not read image dimensions: {}", e.getMessage());
      return null;
    }
  }

  private static int[] readViaImageIO(InputStream data) throws IOException {
    var iis = ImageIO.createImageInputStream(data);
    if (iis == null) return null;
    try (iis) {
      var readers = ImageIO.getImageReaders(iis);
      if (!readers.hasNext()) return null;
      var reader = readers.next();
      try {
        reader.setInput(iis);
        return new int[] {reader.getWidth(0), reader.getHeight(0)};
      } finally {
        reader.dispose();
      }
    }
  }
}
