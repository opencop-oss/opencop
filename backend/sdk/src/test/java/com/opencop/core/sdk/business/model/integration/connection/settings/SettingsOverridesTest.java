// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.connection.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SettingsOverridesTest {

  @Test
  void equalsOtherOverridesWithTheSameSectionsAndFields() {
    var first = new SettingsOverrides(Map.of("presence", Map.of("callsign", "Alpha")));
    var second = new SettingsOverrides(Map.of("presence", Map.of("callsign", "Alpha")));

    assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
    assertThat(first)
        .isNotEqualTo(new SettingsOverrides(Map.of("presence", Map.of("callsign", "B"))));
  }

  /** The table refuses an empty object, so a connection without overrides stores no row. */
  @Test
  void rejectsOverridesWithoutASection() {
    assertThatThrownBy(() -> new SettingsOverrides(Map.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /** A stored value that no longer resolves is resolving's to report, so it is kept as stored. */
  @Test
  void keepsStoredValuesAsTheyAreNullsIncluded() {
    var fields = new LinkedHashMap<String, Object>();
    fields.put("callsign", null);
    fields.put("staleSeconds", 30);

    var overrides = new SettingsOverrides(Map.of("presence", fields));

    assertThat(overrides.sections().get("presence"))
        .containsEntry("callsign", null)
        .containsEntry("staleSeconds", 30);
  }

  @Test
  void isNotChangedByLaterChangesToWhatItWasBuiltFrom() {
    var fields = new HashMap<String, Object>(Map.of("callsign", "Alpha"));
    var sections = new HashMap<String, Map<String, Object>>(Map.of("presence", fields));
    var overrides = new SettingsOverrides(sections);

    fields.put("callsign", "Bravo");
    sections.put("other", Map.of());

    assertThat(overrides.sections()).isEqualTo(Map.of("presence", Map.of("callsign", "Alpha")));
    assertThatThrownBy(() -> overrides.sections().get("presence").put("callsign", "Charlie"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void storesResolvedOverridesInTheirWireForm() {
    var resolved =
        new SettingsDocument(
            Map.of("presence", Map.of("stale", Duration.ofSeconds(30), "radius", 5.0)));

    assertThat(SettingsOverrides.of(resolved))
        .contains(
            new SettingsOverrides(Map.of("presence", Map.of("stale", "PT30S", "radius", 5L))));
  }

  @Test
  void storesNothingForResolvedOverridesThatOverrideNothing() {
    assertThat(SettingsOverrides.of(SettingsDocument.empty())).isEmpty();
  }
}
