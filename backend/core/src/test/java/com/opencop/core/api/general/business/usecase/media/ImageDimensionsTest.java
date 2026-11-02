// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.media;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ImageDimensionsTest {

  @Test
  void webp_readsDimensions() throws Exception {
    assertArrayEquals(new int[] {16, 12}, ImageDimensions.read(resourceBytes("sample.webp")));
  }

  @Test
  void jpeg_readsDimensions() throws Exception {
    assertArrayEquals(new int[] {16, 12}, ImageDimensions.read(resourceBytes("sample.jpg")));
  }

  @Test
  void stream_readsAndResets() throws Exception {
    var bytes = resourceBytes("sample.webp");
    var stream = new ByteArrayInputStream(bytes);

    assertArrayEquals(new int[] {16, 12}, ImageDimensions.read(stream));

    // Stream should be reset — full content still readable
    assertArrayEquals(bytes, stream.readAllBytes());
  }

  @Test
  void webp_preservesExactFourQuadrantPattern() throws Exception {
    var image = decode("sample.webp");

    for (int y = 0; y < 12; y++) {
      for (int x = 0; x < 16; x++) {
        var expected = expectedColor(x, y).getRGB();
        assertEquals(expected, image.getRGB(x, y));
      }
    }
  }

  @Test
  void jpeg_preservesFourQuadrantPatternAtRepresentativePixels() throws Exception {
    var image = decode("sample.jpg");

    assertPixelNear(Color.RED, image, 2, 2);
    assertPixelNear(Color.GREEN, image, 13, 2);
    assertPixelNear(Color.BLUE, image, 2, 9);
    assertPixelNear(Color.WHITE, image, 13, 9);
  }

  @Test
  void invalidData_returnsNull() {
    assertNull(ImageDimensions.read(new byte[] {1, 2, 3}));
  }

  @Test
  void emptyData_returnsNull() {
    assertNull(ImageDimensions.read(new byte[0]));
  }

  @Test
  void syntheticSvg_returnsNull() {
    // SVG is a valid image format but ImageIO has no built-in SVG reader.
    // Documenting this behavior: read() returns null for SVG.
    var bytes = SyntheticMediaFixtures.svg();
    assertNull(ImageDimensions.read(bytes));
  }

  private static byte[] resourceBytes(String filename) throws IOException {
    try (InputStream stream = ImageDimensionsTest.class.getResourceAsStream("/media/" + filename)) {
      assertNotNull(stream, "missing test resource " + filename);
      return stream.readAllBytes();
    }
  }

  private static BufferedImage decode(String filename) throws IOException {
    try (var stream = new ByteArrayInputStream(resourceBytes(filename))) {
      var image = ImageIO.read(stream);
      assertNotNull(image, "could not decode " + filename);
      return image;
    }
  }

  private static Color expectedColor(int x, int y) {
    if (y < 6) {
      return x < 8 ? Color.RED : Color.GREEN;
    }
    return x < 8 ? Color.BLUE : Color.WHITE;
  }

  private static void assertPixelNear(Color expected, BufferedImage image, int x, int y) {
    var actual = new Color(image.getRGB(x, y));
    assertTrue(Math.abs(expected.getRed() - actual.getRed()) <= 10);
    assertTrue(Math.abs(expected.getGreen() - actual.getGreen()) <= 10);
    assertTrue(Math.abs(expected.getBlue() - actual.getBlue()) <= 10);
  }
}
