// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.geocoding;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Locale;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class AcceptLanguageHeaderTest {

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "de-DE,de;q=0.9 | de-DE de",
        "fr;q=0.5, ja;q=0.8, *;q=0.9 | ja fr",
        "*, zh-CN;q=0.7 | zh-CN",
        "not a tag!, es | es",
        "en;q=0, it | it"
      })
  void listsTheHeadersLanguagesByQuality(String header, String expected) {
    assertThat(AcceptLanguageHeader.preferredLanguages(header))
        .containsExactly(
            Arrays.stream(expected.split(" ")).map(Locale::forLanguageTag).toArray(Locale[]::new));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "garbage!!", "*", "de;q=0", ";;;", " , "})
  void findsNoLanguageInAMissingOrUnusableHeader(String header) {
    assertThat(AcceptLanguageHeader.preferredLanguages(header)).isEmpty();
  }
}
