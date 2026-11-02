// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.watchbox.business.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.opencop.core.api.watchbox.business.model.WatchboxSettings;
import com.opencop.core.api.watchbox.ports.gateway.WatchboxLayerAccess;
import com.opencop.core.sdk.model.watchbox.WatchboxCombine;
import com.opencop.core.sdk.model.watchbox.WatchboxConfigurationLimits;
import com.opencop.core.sdk.model.watchbox.WatchboxErrorCode;
import com.opencop.core.sdk.model.watchbox.WatchboxFilter;
import com.opencop.core.sdk.model.watchbox.WatchboxFilterOperation;
import com.opencop.core.sdk.model.watchbox.WatchboxFilterPredicate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class WatchboxValidationServiceLimitsTest {

  private final WatchboxLayerAccess layerAccess = mock(WatchboxLayerAccess.class);
  private final WatchboxValidationService validationService =
      new WatchboxValidationService(new WatchboxSettings(1_000), layerAccess);

  @Test
  void validateLayerIds_acceptsMaximumAndRejectsNextBeforeAccessLookup() {
    Set<UUID> maximum = layerIds(WatchboxConfigurationLimits.MAX_LAYER_IDS);
    assertThat(validationService.validateLayerIds(maximum))
        .containsExactlyInAnyOrderElementsOf(maximum);

    assertThatThrownBy(
            () ->
                validationService.validateLayerIds(
                    layerIds(WatchboxConfigurationLimits.MAX_LAYER_IDS + 1)))
        .isInstanceOfSatisfying(
            WatchboxException.class,
            exception -> {
              assertThat(exception.code())
                  .isEqualTo(WatchboxErrorCode.WATCHBOX_LAYER_LIMIT_EXCEEDED);
              assertThat(exception.fields()).containsExactly("layerIds");
            });
    verifyNoInteractions(layerAccess);
  }

  @Test
  void validateFilter_acceptsMaximumPredicatesAndRejectsNext() {
    assertThat(
            validationService
                .validateFilter(filterOf(WatchboxConfigurationLimits.MAX_FILTER_PREDICATES))
                .predicates())
        .hasSize(WatchboxConfigurationLimits.MAX_FILTER_PREDICATES);

    assertThatThrownBy(
            () ->
                validationService.validateFilter(
                    filterOf(WatchboxConfigurationLimits.MAX_FILTER_PREDICATES + 1)))
        .isInstanceOfSatisfying(
            WatchboxException.class,
            exception -> {
              assertThat(exception.code())
                  .isEqualTo(WatchboxErrorCode.WATCHBOX_FILTER_LIMIT_EXCEEDED);
              assertThat(exception.fields()).containsExactly("filter.predicates");
            });
  }

  private static WatchboxFilter filterOf(int count) {
    return new WatchboxFilter(
        WatchboxCombine.ALL,
        IntStream.range(0, count)
            .mapToObj(
                i ->
                    new WatchboxFilterPredicate(
                        "properties.opencop.attributes.attr" + i,
                        WatchboxFilterOperation.EQUALS,
                        List.of("value-" + i),
                        false,
                        null,
                        null,
                        false))
            .toList());
  }

  private static Set<UUID> layerIds(int count) {
    return IntStream.range(0, count)
        .mapToObj(_ -> UUID.randomUUID())
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }
}
