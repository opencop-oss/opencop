// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.policy.connection.tcp;

import com.opencop.core.api.general.business.model.connection.create.TcpFramingSelection;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionExceptionCodes;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionManagementException;
import com.opencop.core.sdk.business.model.integration.connection.setup.ResolvedTcpFraming;
import com.opencop.core.sdk.business.model.profile.DelimiterModes;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What a delimiter a TCP connection supplies itself must be. A delimiter mode splits on its
 * delimiter, so it needs one; passthrough and binary split on nothing, so they take none.
 *
 * <p>The delimiter is stored exactly as received: the client sends the real characters, and no
 * escape sequence is decoded, so what a user types is what the stream is split on.
 */
public final class TcpCustomFramingPolicy {

  /** The longest delimiter a connection may split on, in UTF-8 bytes. */
  public static final int MAX_DELIMITER_BYTES = 16;

  private static final Set<DelimiterModes> DELIMITED =
      Set.of(DelimiterModes.DELIMITER_INCLUDE, DelimiterModes.DELIMITER_SPLIT);
  private static final String MODES =
      Arrays.stream(DelimiterModes.values())
          .map(DelimiterModes::mode)
          .collect(Collectors.joining(", "));

  private static final String MISSING_MODE_MESSAGE = "Custom framing needs a mode: one of " + MODES;
  private static final String UNEXPECTED_DELIMITER_MESSAGE = "Mode '%s' takes no delimiter";
  private static final String MISSING_DELIMITER_MESSAGE = "Mode '%s' requires a delimiter";
  private static final String DELIMITER_TOO_LONG_MESSAGE =
      "A delimiter is at most " + MAX_DELIMITER_BYTES + " bytes";

  // A mode that splits on nothing is stored with an empty delimiter, as existing connections
  // already are, so every connection reads the same shape.
  private static final String NO_DELIMITER = "";

  private TcpCustomFramingPolicy() {}

  /** The framing the selection describes, once it is known to be a usable custom framing. */
  public static ResolvedTcpFraming.Custom require(TcpFramingSelection.Custom selection) {
    var mode =
        DelimiterModes.find(selection.mode())
            .orElseThrow(
                () -> rejection(TcpFramingSelection.Custom.MODE_FIELD, MISSING_MODE_MESSAGE));
    var delimiter = selection.delimiter();
    var hasDelimiter = delimiter != null && !delimiter.isEmpty();
    if (!DELIMITED.contains(mode)) {
      if (hasDelimiter) {
        throw rejection(
            TcpFramingSelection.Custom.DELIMITER_FIELD,
            UNEXPECTED_DELIMITER_MESSAGE.formatted(mode.mode()));
      }
      return new ResolvedTcpFraming.Custom(mode, NO_DELIMITER);
    }
    if (!hasDelimiter) {
      throw rejection(
          TcpFramingSelection.Custom.DELIMITER_FIELD,
          MISSING_DELIMITER_MESSAGE.formatted(mode.mode()));
    }
    if (delimiter.getBytes(StandardCharsets.UTF_8).length > MAX_DELIMITER_BYTES) {
      throw rejection(TcpFramingSelection.Custom.DELIMITER_FIELD, DELIMITER_TOO_LONG_MESSAGE);
    }
    return new ResolvedTcpFraming.Custom(mode, delimiter);
  }

  private static ConnectionManagementException rejection(String field, String reason) {
    return ConnectionManagementException.forField(
        ConnectionExceptionCodes.INVALID_CUSTOM_FRAMING, field, reason);
  }
}
