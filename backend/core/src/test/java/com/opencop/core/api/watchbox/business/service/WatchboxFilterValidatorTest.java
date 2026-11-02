// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.watchbox.business.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.opencop.core.api.watchbox.business.model.WatchboxSettings;
import com.opencop.core.api.watchbox.ports.gateway.WatchboxLayerAccess;
import com.opencop.core.sdk.model.watchbox.WatchboxCombine;
import com.opencop.core.sdk.model.watchbox.WatchboxErrorCode;
import com.opencop.core.sdk.model.watchbox.WatchboxFilter;
import com.opencop.core.sdk.model.watchbox.WatchboxFilterOperation;
import com.opencop.core.sdk.model.watchbox.WatchboxFilterPredicate;
import java.util.List;
import org.junit.jupiter.api.Test;

class WatchboxFilterValidatorTest {

  private final WatchboxValidationService validator = validator();

  private static WatchboxValidationService validator() {
    WatchboxLayerAccess layerAccess = mock(WatchboxLayerAccess.class);
    return new WatchboxValidationService(new WatchboxSettings(2_000), layerAccess);
  }

  @Test
  void acceptsAllowedPathsAndOperations() {
    var filter =
        new WatchboxFilter(
            WatchboxCombine.ALL,
            List.of(
                new WatchboxFilterPredicate(
                    "properties.opencop.kind", WatchboxFilterOperation.EQUALS, List.of("unit")),
                new WatchboxFilterPredicate(
                    "properties.opencop.attributes.battery-level",
                    WatchboxFilterOperation.SUBSTRING_IN,
                    List.of("h"),
                    false,
                    1,
                    1,
                    false),
                new WatchboxFilterPredicate(
                    "properties.opencop.element.symbol",
                    WatchboxFilterOperation.IN,
                    List.of(
                        "10061000001211000000", "10061000001212000000", "10061000001207000000")),
                new WatchboxFilterPredicate(
                    "properties.opencop.displayName", WatchboxFilterOperation.EXISTS, List.of())));

    assertThat(validator.validateFilter(filter).predicates()).hasSize(4);
  }

  @Test
  void rejectsUnsupportedPath() {
    var filter =
        new WatchboxFilter(
            WatchboxCombine.ALL,
            List.of(
                new WatchboxFilterPredicate(
                    "$.raw.jsonpath", WatchboxFilterOperation.EQUALS, List.of("x"))));

    assertInvalidFilter(filter);
  }

  @Test
  void rejectsInvalidValueCountsAndOverlongValues() {
    assertInvalidFilter(
        new WatchboxFilter(
            WatchboxCombine.ALL,
            List.of(
                new WatchboxFilterPredicate(
                    "properties.opencop.kind", WatchboxFilterOperation.EXISTS, List.of("x")))));

    assertInvalidFilter(
        new WatchboxFilter(
            WatchboxCombine.ALL,
            List.of(
                new WatchboxFilterPredicate(
                    "properties.opencop.kind", WatchboxFilterOperation.IN, List.of()))));

    assertInvalidFilter(
        new WatchboxFilter(
            WatchboxCombine.ALL,
            List.of(
                new WatchboxFilterPredicate(
                    "properties.opencop.kind",
                    WatchboxFilterOperation.EQUALS,
                    List.of("x".repeat(129))))));
  }

  @Test
  void rejectsSubstringInWithoutRequiredWindow() {
    assertInvalidFilter(
        new WatchboxFilter(
            WatchboxCombine.ALL,
            List.of(
                new WatchboxFilterPredicate(
                    "properties.opencop.element.symbol",
                    WatchboxFilterOperation.SUBSTRING_IN,
                    List.of("h")))));
  }

  @Test
  void rejectsPositionMetadataOnNonSubstringOperations() {
    assertInvalidFilter(
        new WatchboxFilter(
            WatchboxCombine.ALL,
            List.of(
                new WatchboxFilterPredicate(
                    "properties.opencop.kind",
                    WatchboxFilterOperation.EQUALS,
                    List.of("unit"),
                    false,
                    1,
                    null,
                    false))));
  }

  @Test
  void rejectsSubstringInWindowThatCanOverflowOrExceedBound() {
    assertInvalidFilter(
        new WatchboxFilter(
            WatchboxCombine.ALL,
            List.of(
                new WatchboxFilterPredicate(
                    "properties.opencop.element.symbol",
                    WatchboxFilterOperation.SUBSTRING_IN,
                    List.of("h"),
                    false,
                    Integer.MAX_VALUE,
                    1,
                    false))));

    assertInvalidFilter(
        new WatchboxFilter(
            WatchboxCombine.ALL,
            List.of(
                new WatchboxFilterPredicate(
                    "properties.opencop.element.symbol",
                    WatchboxFilterOperation.SUBSTRING_IN,
                    List.of("h"),
                    false,
                    127,
                    2,
                    false))));
  }

  @Test
  void rejectsDottedDynamicAttributeSuffixes() {
    assertThat(
            validator
                .validateFilter(
                    new WatchboxFilter(
                        WatchboxCombine.ALL,
                        List.of(
                            new WatchboxFilterPredicate(
                                "properties.opencop.attributes.battery-level",
                                WatchboxFilterOperation.EXISTS,
                                List.of()))))
                .predicates())
        .hasSize(1);

    assertInvalidFilter(
        new WatchboxFilter(
            WatchboxCombine.ALL,
            List.of(
                new WatchboxFilterPredicate(
                    "properties.opencop.attributes.foo.bar",
                    WatchboxFilterOperation.EXISTS,
                    List.of()))));
  }

  @Test
  void rejectsNonEmptyFilterWithoutCombine() {
    assertInvalidFilter(
        new WatchboxFilter(
            null,
            List.of(
                new WatchboxFilterPredicate(
                    "properties.opencop.kind", WatchboxFilterOperation.EXISTS, List.of()))));
  }

  private void assertInvalidFilter(WatchboxFilter filter) {
    assertThatThrownBy(() -> validator.validateFilter(filter))
        .isInstanceOfSatisfying(
            WatchboxException.class,
            ex -> assertThat(ex.code()).isEqualTo(WatchboxErrorCode.WATCHBOX_INVALID_FILTER));
  }
}
