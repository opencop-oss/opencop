// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.policy.connection.tcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.core.api.general.business.model.connection.create.TcpFramingSelection;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionExceptionCodes;
import com.opencop.core.sdk.business.model.integration.connection.setup.ResolvedTcpFraming;
import com.opencop.core.sdk.business.model.profile.DelimiterModes;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import org.junit.jupiter.api.Test;

class TcpCustomFramingPolicyTest {

  private static final String SIXTEEN_BYTES = "</event-end-xx>!";
  private static final String SEVENTEEN_BYTES = SIXTEEN_BYTES + "?";

  @Test
  void keepsADelimiterExactlyAsReceived() {
    assertThat(require("delimiterInclude", "</event>"))
        .isEqualTo(new ResolvedTcpFraming.Custom(DelimiterModes.DELIMITER_INCLUDE, "</event>"));
    assertThat(require("delimiterSplit", "\n"))
        .isEqualTo(new ResolvedTcpFraming.Custom(DelimiterModes.DELIMITER_SPLIT, "\n"));
    assertThat(require("delimiterSplit", "\\n").delimiter()).isEqualTo("\\n");
  }

  @Test
  void acceptsPassthroughAndBinaryWithoutADelimiter() {
    assertThat(require("passthrough", null))
        .isEqualTo(new ResolvedTcpFraming.Custom(DelimiterModes.PASSTHROUGH, ""));
    assertThat(require("binary", ""))
        .isEqualTo(new ResolvedTcpFraming.Custom(DelimiterModes.BINARY, ""));
  }

  @Test
  void rejectsAMissingOrUnknownMode() {
    assertRejected(null, "</event>", TcpFramingSelection.Custom.MODE_FIELD);
    assertRejected("lines", "</event>", TcpFramingSelection.Custom.MODE_FIELD);
  }

  @Test
  void requiresADelimiterForTheDelimiterModes() {
    assertRejected("delimiterInclude", null, TcpFramingSelection.Custom.DELIMITER_FIELD);
    assertRejected("delimiterSplit", "", TcpFramingSelection.Custom.DELIMITER_FIELD);
  }

  @Test
  void rejectsADelimiterForTheModesThatSplitOnNothing() {
    assertRejected("passthrough", "\n", TcpFramingSelection.Custom.DELIMITER_FIELD);
    assertRejected("binary", "</event>", TcpFramingSelection.Custom.DELIMITER_FIELD);
  }

  @Test
  void limitsTheDelimiterToSixteenBytes() {
    assertThat(SIXTEEN_BYTES).hasSize(TcpCustomFramingPolicy.MAX_DELIMITER_BYTES);
    assertThat(require("delimiterInclude", SIXTEEN_BYTES).delimiter()).isEqualTo(SIXTEEN_BYTES);
    assertRejected("delimiterInclude", SEVENTEEN_BYTES, TcpFramingSelection.Custom.DELIMITER_FIELD);
  }

  /** The limit counts bytes, so a delimiter of few multi-byte characters can still exceed it. */
  @Test
  void countsTheLimitInUtf8Bytes() {
    assertRejected("delimiterSplit", "€€€€€€", TcpFramingSelection.Custom.DELIMITER_FIELD);
  }

  private static ResolvedTcpFraming.Custom require(String mode, String delimiter) {
    return TcpCustomFramingPolicy.require(new TcpFramingSelection.Custom(mode, delimiter));
  }

  private static void assertRejected(String mode, String delimiter, String field) {
    assertThatThrownBy(() -> require(mode, delimiter))
        .isInstanceOfSatisfying(
            GlobalOpenCopException.class,
            exception -> {
              assertThat(exception.getCode())
                  .isEqualTo(ConnectionExceptionCodes.INVALID_CUSTOM_FRAMING.code());
              assertThat(exception.getField()).isEqualTo(field);
            });
  }
}
