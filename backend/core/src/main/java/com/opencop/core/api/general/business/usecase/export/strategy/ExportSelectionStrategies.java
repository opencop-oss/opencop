// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.export.strategy;

import com.opencop.core.api.general.business.export.ExportSelection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ExportSelectionStrategies {

  private final Map<ExportSelection.Mode, ExportSelectionStrategy> strategiesByMode;

  public ExportSelectionStrategies(List<ExportSelectionStrategy> strategies) {
    var map =
        new EnumMap<ExportSelection.Mode, ExportSelectionStrategy>(ExportSelection.Mode.class);
    for (var strategy : strategies) {
      var previous = map.put(strategy.supportedMode(), strategy);
      if (previous != null) {
        throw new IllegalStateException(
            "Duplicate export selection strategy for mode " + strategy.supportedMode());
      }
    }
    for (var mode : ExportSelection.Mode.values()) {
      if (!map.containsKey(mode)) {
        throw new IllegalStateException("No export selection strategy registered for mode " + mode);
      }
    }
    this.strategiesByMode = Map.copyOf(map);
  }

  public ExportSelectionStrategy forMode(ExportSelection.Mode mode) {
    return strategiesByMode.get(mode);
  }
}
