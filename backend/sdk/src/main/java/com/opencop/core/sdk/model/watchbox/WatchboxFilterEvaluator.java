// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.watchbox;

import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;

public final class WatchboxFilterEvaluator {

  private WatchboxFilterEvaluator() {}

  /**
   * Evaluates a filter against an event-like JSON object. Filter paths are dot-separated JSON paths
   * from that root, e.g. properties.opencop.element.symbol.
   */
  public static WatchboxFilterMatch evaluate(WatchboxFilter filter, JsonNode featureNode) {
    WatchboxFilter effectiveFilter = filter == null ? WatchboxFilter.empty() : filter;
    List<WatchboxFilterPredicate> predicates = effectiveFilter.predicates();
    if (predicates.isEmpty()) {
      return new WatchboxFilterMatch(true, List.of());
    }

    List<WatchboxFilterPredicateResult> results = new ArrayList<>(predicates.size());
    for (int i = 0; i < predicates.size(); i++) {
      WatchboxFilterPredicate predicate = predicates.get(i);
      boolean matched = evaluatePredicate(predicate, featureNode);
      if (predicate.negate()) {
        matched = !matched;
      }
      results.add(
          new WatchboxFilterPredicateResult(
              i, predicate.path(), predicate.operation(), predicate.negate(), matched));
    }

    WatchboxCombine combine =
        effectiveFilter.combine() == null ? WatchboxCombine.ALL : effectiveFilter.combine();
    boolean matches =
        switch (combine) {
          case ALL -> results.stream().allMatch(WatchboxFilterPredicateResult::matched);
          case ANY -> results.stream().anyMatch(WatchboxFilterPredicateResult::matched);
        };
    return new WatchboxFilterMatch(matches, results);
  }

  private static boolean evaluatePredicate(
      WatchboxFilterPredicate predicate, JsonNode featureNode) {
    JsonNode value = resolve(featureNode, predicate.path());
    if (predicate.operation() == null) {
      return false;
    }

    return switch (predicate.operation()) {
      case EXISTS -> value != null && !value.isNull();
      case EQUALS -> matchesText(value, predicate.values(), predicate.caseInsensitive(), true);
      case IN -> matchesText(value, predicate.values(), predicate.caseInsensitive(), false);
      case SUBSTRING_IN -> substringIn(value, predicate);
    };
  }

  private static JsonNode resolve(JsonNode root, String path) {
    if (root == null || path == null || path.isBlank()) {
      return null;
    }

    JsonNode cursor = root;
    for (String segment : path.split("\\.")) {
      if (cursor == null || cursor.isNull()) {
        return null;
      }
      cursor = cursor.get(segment);
    }
    return cursor;
  }

  private static boolean matchesText(
      JsonNode node, List<String> values, boolean caseInsensitive, boolean requireSingleValue) {
    if (node == null || node.isNull() || !node.isString()) {
      return false;
    }
    if (values == null || values.isEmpty() || (requireSingleValue && values.size() != 1)) {
      return false;
    }

    String text = node.asText();
    return values.stream().anyMatch(candidate -> equal(text, candidate, caseInsensitive));
  }

  private static boolean substringIn(JsonNode node, WatchboxFilterPredicate predicate) {
    if (node == null || node.isNull() || !node.isString()) {
      return false;
    }
    if (predicate.offset() == null || predicate.length() == null || predicate.offset() < 0) {
      return false;
    }

    String text = node.asText();
    long end = (long) predicate.offset() + predicate.length();
    if (predicate.length() < 1 || end > text.length()) {
      return false;
    }

    String substring = text.substring(predicate.offset(), (int) end);
    return predicate.values().stream()
        .anyMatch(candidate -> equal(substring, candidate, predicate.caseInsensitive()));
  }

  private static boolean equal(String actual, String expected, boolean caseInsensitive) {
    if (expected == null) {
      return false;
    }
    return caseInsensitive ? actual.equalsIgnoreCase(expected) : actual.equals(expected);
  }
}
