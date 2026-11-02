// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.helper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class LocaleSupportTest {

  @ParameterizedTest
  @CsvSource({
    "en, en",
    "EN-us, en-US",
    "' en-GB ', en-GB",
    "zh-Hant-TW, zh-Hant-TW",
    "de-DE-u-co-phonebk, de-DE-u-co-phonebk",
    "i-klingon, tlh"
  })
  void parsesAWellFormedLanguageTag(String rawTag, String expectedTag) {
    assertThat(LocaleSupport.parseLanguageTag(rawTag))
        .hasValueSatisfying(locale -> assertThat(locale.toLanguageTag()).isEqualTo(expectedTag));
  }

  @ParameterizedTest
  @ValueSource(strings = {"zh-CN-!", "en--US", "en-US-u", "en-", "en-@", "en_US", "%%%", "123"})
  void refusesMalformedSyntaxInsteadOfDiscardingIt(String rawTag) {
    assertThat(LocaleSupport.parseLanguageTag(rawTag)).isEmpty();
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "  ", "und", "x-private"})
  void refusesATagThatNamesNoLanguage(String rawTag) {
    assertThat(LocaleSupport.parseLanguageTag(rawTag)).isEmpty();
  }
}
