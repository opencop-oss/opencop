// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.watchbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class WatchboxFilterEvaluatorTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void substringInMatchesHostileSymbolGoldenVector() {
    WatchboxFilterMatch result =
        WatchboxFilterEvaluator.evaluate(
            hostileSymbolFilter(), featureWithSymbol("shgp-----------"));

    assertThat(result.matches()).isTrue();
    assertThat(result.predicateResults())
        .containsExactly(
            new WatchboxFilterPredicateResult(
                0,
                "properties.opencop.element.symbol",
                WatchboxFilterOperation.SUBSTRING_IN,
                false,
                true));
  }

  @Test
  void substringInRejectsFriendlySymbolGoldenVector() {
    WatchboxFilterMatch result =
        WatchboxFilterEvaluator.evaluate(
            hostileSymbolFilter(), featureWithSymbol("sfgp-----------"));

    assertThat(result.matches()).isFalse();
    assertThat(result.predicateResults()).singleElement().extracting("matched").isEqualTo(false);
  }

  @Test
  void substringInDoesNotMatchAMediaIcon() {
    WatchboxFilterMatch result =
        WatchboxFilterEvaluator.evaluate(hostileSymbolFilter(), mediaIconFeature("shgp/icon.png"));

    assertThat(result.matches()).isFalse();
    assertThat(result.predicateResults()).singleElement().extracting("matched").isEqualTo(false);
  }

  @Test
  void existsUsesLiteralPresenceSemanticsAndNegateFlipsResult() {
    assertThat(
            evaluate(exists("properties.opencop.missing"), featureWithDisplayName("Alpha"))
                .matches())
        .isFalse();
    assertThat(
            evaluate(exists("properties.opencop.displayName"), featureWithDisplayName(null))
                .matches())
        .isFalse();
    assertThat(
            evaluate(exists("properties.opencop.displayName"), featureWithDisplayName(""))
                .matches())
        .isTrue();
    assertThat(
            evaluate(exists("properties.opencop.displayName"), featureWithDisplayName("Alpha"))
                .matches())
        .isTrue();

    WatchboxFilterPredicate negated =
        new WatchboxFilterPredicate(
            "properties.opencop.displayName",
            WatchboxFilterOperation.EXISTS,
            List.of(),
            true,
            null,
            null,
            false);

    assertThat(evaluate(negated, featureWithDisplayName("Alpha")).matches()).isFalse();
    assertThat(evaluate(negated, featureWithDisplayName(null)).matches()).isTrue();
  }

  @Test
  void caseInsensitiveAppliesToSubstringInComparisons() {
    WatchboxFilterPredicate caseSensitive =
        new WatchboxFilterPredicate(
            "properties.opencop.element.symbol",
            WatchboxFilterOperation.SUBSTRING_IN,
            List.of("H"),
            false,
            1,
            1,
            false);
    WatchboxFilterPredicate caseInsensitive =
        new WatchboxFilterPredicate(
            "properties.opencop.element.symbol",
            WatchboxFilterOperation.SUBSTRING_IN,
            List.of("H"),
            false,
            1,
            1,
            true);

    assertThat(evaluate(caseSensitive, featureWithSymbol("shgp-----------")).matches()).isFalse();
    assertThat(evaluate(caseInsensitive, featureWithSymbol("shgp-----------")).matches()).isTrue();
  }

  @Test
  void combineAllAndAnyUsePredicateResults() {
    WatchboxFilterPredicate matching =
        new WatchboxFilterPredicate(
            "properties.opencop.element.symbol",
            WatchboxFilterOperation.SUBSTRING_IN,
            List.of("h"),
            false,
            1,
            1,
            false);
    WatchboxFilterPredicate notMatching =
        new WatchboxFilterPredicate(
            "properties.opencop.displayName", WatchboxFilterOperation.EQUALS, List.of("Bravo"));
    JsonNode feature = featureWithSymbolAndDisplayName("shgp-----------", "Alpha");

    assertThat(
            WatchboxFilterEvaluator.evaluate(
                    new WatchboxFilter(WatchboxCombine.ALL, List.of(matching, notMatching)),
                    feature)
                .matches())
        .isFalse();
    assertThat(
            WatchboxFilterEvaluator.evaluate(
                    new WatchboxFilter(WatchboxCombine.ANY, List.of(matching, notMatching)),
                    feature)
                .matches())
        .isTrue();
  }

  @Test
  void substringInOutOfRangeDoesNotThrowAndDoesNotMatch() {
    WatchboxFilterPredicate predicate =
        new WatchboxFilterPredicate(
            "properties.opencop.element.symbol",
            WatchboxFilterOperation.SUBSTRING_IN,
            List.of("h"),
            false,
            100,
            1,
            false);

    assertThat(evaluate(predicate, featureWithSymbol("shgp-----------")).matches()).isFalse();
  }

  @Test
  void substringInZeroLengthDoesNotThrowAndDoesNotMatch() {
    WatchboxFilterPredicate predicate =
        new WatchboxFilterPredicate(
            "properties.opencop.element.symbol",
            WatchboxFilterOperation.SUBSTRING_IN,
            List.of(""),
            false,
            0,
            0,
            false);

    assertThat(evaluate(predicate, featureWithSymbol("shgp-----------")).matches()).isFalse();
  }

  @Test
  void emptyPredicateListMatchesEverything() {
    assertThat(
            WatchboxFilterEvaluator.evaluate(
                    new WatchboxFilter(WatchboxCombine.ANY, List.of()),
                    featureWithSymbol("sfgp-----------"))
                .matches())
        .isTrue();
  }

  private WatchboxFilter hostileSymbolFilter() {
    return new WatchboxFilter(
        WatchboxCombine.ALL,
        List.of(
            new WatchboxFilterPredicate(
                "properties.opencop.element.symbol",
                WatchboxFilterOperation.SUBSTRING_IN,
                List.of("h"),
                false,
                1,
                1,
                false)));
  }

  private WatchboxFilterMatch evaluate(WatchboxFilterPredicate predicate, JsonNode feature) {
    return WatchboxFilterEvaluator.evaluate(
        new WatchboxFilter(WatchboxCombine.ALL, List.of(predicate)), feature);
  }

  private WatchboxFilterPredicate exists(String path) {
    return new WatchboxFilterPredicate(path, WatchboxFilterOperation.EXISTS, List.of());
  }

  private JsonNode featureWithSymbol(String symbol) {
    return featureWithSymbolAndDisplayName(symbol, "Track");
  }

  private JsonNode featureWithDisplayName(String displayName) {
    return featureWithSymbolAndDisplayName("shgp-----------", displayName);
  }

  private JsonNode featureWithSymbolAndDisplayName(String symbol, String displayName) {
    return readTree(
        """
        {
          "type": "Feature",
          "properties": {
            "opencop": {
              "displayName": %s,
              "element": {
                "type": "symbol",
                "symbol": "%s"
              }
            }
          },
          "geometry": {
            "type": "Point",
            "coordinates": [-100.0, 40.0]
          }
        }
        """
            .formatted(displayName == null ? "null" : "\"" + displayName + "\"", symbol));
  }

  private JsonNode mediaIconFeature(String href) {
    return readTree(
        """
        {
          "type": "Feature",
          "properties": {
            "opencop": {
              "displayName": "Icon",
              "element": {
                "type": "media-icon",
                "href": "%s"
              }
            }
          },
          "geometry": {
            "type": "Point",
            "coordinates": [-100.0, 40.0]
          }
        }
        """
            .formatted(href));
  }

  private JsonNode readTree(String json) {
    try {
      return objectMapper.readTree(json);
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }
}
