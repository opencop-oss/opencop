// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.watchbox.business.factory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.core.api.watchbox.business.service.WatchboxException;
import com.opencop.core.sdk.model.watchbox.WatchboxConfigurationLimits;
import com.opencop.core.sdk.model.watchbox.WatchboxErrorCode;
import org.junit.jupiter.api.Test;

class WatchboxConfigFactoryTest {

  private final WatchboxConfigFactory factory = new WatchboxConfigFactory();

  @Test
  void normalizeName_measuresTrimmedUnicodeCodePointsAtDurableBoundary() {
    String supplementaryCodePoint = "\uD83D\uDE80";
    String bounded =
        supplementaryCodePoint.repeat(WatchboxConfigurationLimits.MAX_NAME_CODE_POINTS);

    assertThat(factory.normalizeName("  " + bounded + "  ")).isEqualTo(bounded);
    assertThatThrownBy(() -> factory.normalizeName(bounded + supplementaryCodePoint))
        .isInstanceOfSatisfying(
            WatchboxException.class,
            exception -> {
              assertThat(exception.code()).isEqualTo(WatchboxErrorCode.WATCHBOX_INVALID_NAME);
              assertThat(exception.fields()).containsExactly("name");
            });
  }

  @Test
  void normalizeDescription_preservesNullAndBlankSemanticsAndEnforcesBoundary() {
    String bounded = "d".repeat(WatchboxConfigurationLimits.MAX_DESCRIPTION_CODE_POINTS);

    assertThat(factory.normalizeDescription(null)).isNull();
    assertThat(factory.normalizeDescription("   ")).isNull();
    assertThat(factory.normalizeDescription("  " + bounded + "  ")).isEqualTo(bounded);
    assertThatThrownBy(() -> factory.normalizeDescription(bounded + "d"))
        .isInstanceOfSatisfying(
            WatchboxException.class,
            exception -> {
              assertThat(exception.code())
                  .isEqualTo(WatchboxErrorCode.WATCHBOX_INVALID_DESCRIPTION);
              assertThat(exception.fields()).containsExactly("description");
            });
  }
}
