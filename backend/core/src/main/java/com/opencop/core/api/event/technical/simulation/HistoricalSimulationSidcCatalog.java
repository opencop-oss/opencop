// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.technical.simulation;

import java.util.List;

/**
 * Small, hand-picked subset of SIDC strings used by {@link HistoricalSimulationService}. Covers the
 * four main affiliations (friend, hostile, neutral, unknown) and a few entity types so the
 * generated dataset renders as visually varied symbols on the map. A larger SIDC list lives in the
 * test sources ({@code SimulationTest.SIDC_LIST}) — that one is intentionally not depended on here
 * to keep the production simulation path independent of test code.
 */
final class HistoricalSimulationSidcCatalog {

  private HistoricalSimulationSidcCatalog() {}

  static final List<String> LIST =
      List.of(
          // Friend (3) — infantry / armor / aviation
          "10031000001211000000",
          "10031000001212000000",
          "10031000001207000000",
          // Hostile (6) — infantry / armor / aviation
          "10061000001211000000",
          "10061000001212000000",
          "10061000001207000000",
          // Neutral (4) — infantry / armor / aviation
          "10041000001211000000",
          "10041000001212000000",
          "10041000001207000000",
          // Unknown (1) — infantry / armor / aviation
          "10011000001211000000",
          "10011000001212000000",
          "10011000001207000000");
}
