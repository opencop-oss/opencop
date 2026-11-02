// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.export.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.business.export.ExportSelection;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExportSelectionStrategiesTest {

  @Test
  void forMode_returnsStrategyMatchingSupportedMode() {
    var subjects = strategyFor(ExportSelection.Mode.SUBJECTS);
    var elementIds = strategyFor(ExportSelection.Mode.ELEMENT_IDS);
    var registry = new ExportSelectionStrategies(List.of(subjects, elementIds));

    assertSame(subjects, registry.forMode(ExportSelection.Mode.SUBJECTS));
    assertSame(elementIds, registry.forMode(ExportSelection.Mode.ELEMENT_IDS));
  }

  @Test
  void constructor_whenStrategyMissingForMode_throws() {
    var subjects = strategyFor(ExportSelection.Mode.SUBJECTS);

    var exception =
        assertThrows(
            IllegalStateException.class, () -> new ExportSelectionStrategies(List.of(subjects)));

    assertEquals(
        "No export selection strategy registered for mode ELEMENT_IDS", exception.getMessage());
  }

  @Test
  void constructor_whenDuplicateStrategyForMode_throws() {
    var first = strategyFor(ExportSelection.Mode.SUBJECTS);
    var second = strategyFor(ExportSelection.Mode.SUBJECTS);
    var elementIds = strategyFor(ExportSelection.Mode.ELEMENT_IDS);

    var exception =
        assertThrows(
            IllegalStateException.class,
            () -> new ExportSelectionStrategies(List.of(first, second, elementIds)));

    assertEquals("Duplicate export selection strategy for mode SUBJECTS", exception.getMessage());
  }

  private static ExportSelectionStrategy strategyFor(ExportSelection.Mode mode) {
    var strategy = mock(ExportSelectionStrategy.class);
    when(strategy.supportedMode()).thenReturn(mode);
    return strategy;
  }
}
