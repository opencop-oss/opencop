// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.connection.extension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ExtensionPathTemplateTest {

  @Test
  void literalTemplateMatchesOnlyItsOwnSegments() {
    var template = ExtensionPathTemplate.parse("/missions/active");

    assertThat(template.match(List.of("missions", "active"))).contains(Map.of());
    assertThat(template.match(List.of("missions", "archived"))).isEmpty();
    assertThat(template.match(List.of("missions"))).isEmpty();
    assertThat(template.match(List.of("missions", "active", "extra"))).isEmpty();
  }

  @Test
  void variablesCaptureTheirSegment() {
    var template = ExtensionPathTemplate.parse("/missions/{name}/subscriptions/{uid}");

    assertThat(template.match(List.of("missions", "alpha", "subscriptions", "u-1")))
        .contains(Map.of("name", "alpha", "uid", "u-1"));
  }

  @Test
  void namesItsVariablesInPathOrder() {
    assertThat(ExtensionPathTemplate.parse("/missions/{name}/subscriptions/{uid}").variableNames())
        .containsExactly("name", "uid");
    assertThat(ExtensionPathTemplate.parse("/").variableNames()).isEmpty();
  }

  @Test
  void rootTemplateMatchesNoSegments() {
    var template = ExtensionPathTemplate.parse("/");

    assertThat(template.match(List.of())).contains(Map.of());
    assertThat(template.match(List.of("missions"))).isEmpty();
  }

  @Test
  void templatesDifferingOnlyInVariableNamesHaveTheSameShape() {
    var first = ExtensionPathTemplate.parse("/missions/{name}");
    var second = ExtensionPathTemplate.parse("/missions/{id}");
    var literal = ExtensionPathTemplate.parse("/missions/active");

    assertThat(first.hasSameShapeAs(second)).isTrue();
    assertThat(first.hasSameShapeAs(literal)).isFalse();
  }

  @Test
  void literalSegmentIsMoreSpecificThanAVariableAtTheSamePosition() {
    var variable = ExtensionPathTemplate.parse("/missions/{name}");
    var literal = ExtensionPathTemplate.parse("/missions/active");

    assertThat(ExtensionPathTemplate.MOST_SPECIFIC_FIRST.compare(literal, variable)).isNegative();
    assertThat(ExtensionPathTemplate.MOST_SPECIFIC_FIRST.compare(variable, literal)).isPositive();
  }

  @Test
  void templatesOfDifferentLengthsAreNeverEquallySpecific() {
    var shorter = ExtensionPathTemplate.parse("/missions");
    var longer = ExtensionPathTemplate.parse("/missions/{name}");

    assertThat(ExtensionPathTemplate.MOST_SPECIFIC_FIRST.compare(shorter, longer)).isNotZero();
    assertThat(Integer.signum(ExtensionPathTemplate.MOST_SPECIFIC_FIRST.compare(shorter, longer)))
        .isEqualTo(
            -Integer.signum(ExtensionPathTemplate.MOST_SPECIFIC_FIRST.compare(longer, shorter)));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "missions",
        "/missions/",
        "//missions",
        "/missions/{}",
        "/missions/{1name}",
        "/missions/a{name}",
        "/missions/{name}/{name}",
        "/missions/..",
        "/missions/.",
        "/missions/na}me"
      })
  void malformedTemplatesAreRejected(String template) {
    assertThatThrownBy(() -> ExtensionPathTemplate.parse(template))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void nullTemplateIsRejected() {
    assertThatThrownBy(() -> ExtensionPathTemplate.parse(null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
