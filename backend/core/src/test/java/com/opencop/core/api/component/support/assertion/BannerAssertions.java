// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.support.assertion;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.opencop.core.api.general.technical.model.banner.Banner;
import com.opencop.core.api.general.technical.model.banner.BannerTranslation;
import java.util.List;
import java.util.UUID;

public final class BannerAssertions {

  private BannerAssertions() {}

  public static void assertBannerSummary(
      Banner banner, UUID expectedId, String expectedName, String expectedKind, int translations) {
    assertEquals(expectedId, banner.id());
    assertEquals(expectedName, banner.name());
    assertEquals(expectedKind, banner.kind().name());
    assertEquals(
        translations,
        List.copyOf(banner.translations() == null ? List.of() : banner.translations()).size());
  }

  public static void assertBannerMetadata(
      Banner banner,
      String expectedName,
      boolean enabled,
      String startsAt,
      String endsAt,
      String color,
      String textColor,
      String format,
      boolean dismissible,
      String linkUrl,
      int priority) {
    assertEquals(expectedName, banner.name());
    assertEquals(enabled, banner.enabled());
    assertEquals(startsAt, banner.startsAt().toString());
    assertEquals(endsAt, banner.endsAt().toString());
    assertEquals(color, banner.color());
    assertEquals(textColor, banner.textColor());
    assertEquals(format, banner.format().name());
    assertEquals(dismissible, banner.dismissible());
    assertEquals(linkUrl, banner.linkUrl());
    assertEquals(priority, banner.priority());
  }

  public static void assertTranslation(
      BannerTranslation translation,
      UUID expectedId,
      String expectedLocale,
      String expectedContent) {
    assertEquals(expectedId, translation.id());
    assertEquals(expectedLocale, translation.locale());
    assertEquals(expectedContent, translation.content());
  }

  public static void assertTranslation(
      BannerTranslation translation, String expectedLocale, String expectedContent) {
    assertEquals(expectedLocale, translation.locale());
    assertEquals(expectedContent, translation.content());
  }
}
