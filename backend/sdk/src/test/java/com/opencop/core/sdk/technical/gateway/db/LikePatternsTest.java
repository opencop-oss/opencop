// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class LikePatternsTest {

  @Test
  void startsWith_lowercasesIndependentlyOfDefaultLocale() {
    Locale previous = Locale.getDefault();
    try {
      Locale.setDefault(Locale.forLanguageTag("tr"));
      assertEquals("istanbul%", LikePatterns.startsWith("ISTANBUL"));
      assertEquals("i%", LikePatterns.startsWith("I"));
    } finally {
      Locale.setDefault(previous);
    }
  }

  @Test
  void startsWith_trimsAndEscapesLikeMetacharacters() {
    assertEquals("50\\%\\_x%", LikePatterns.startsWith("  50%_x  "));
  }

  @Test
  void contains_wrapsInWildcardsOnBothSides() {
    assertEquals("%wyman%", LikePatterns.contains("Wyman"));
  }

  @Test
  void contains_trimsAndEscapesLikeMetacharacters() {
    // A user typing % must search for a literal %, not match everything.
    assertEquals("%50\\%\\_x%", LikePatterns.contains("  50%_x  "));
  }

  @Test
  void contains_lowercasesIndependentlyOfDefaultLocale() {
    Locale previous = Locale.getDefault();
    try {
      Locale.setDefault(Locale.forLanguageTag("tr"));
      assertEquals("%istanbul%", LikePatterns.contains("ISTANBUL"));
    } finally {
      Locale.setDefault(previous);
    }
  }

  @Test
  void escape_escapesBackslashBeforeWildcards() {
    assertEquals("a\\\\b\\%c\\_d", LikePatterns.escape("a\\b%c_d"));
  }
}
