// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.connection.create;

import java.util.UUID;

/**
 * How a new TCP connection asks to split its stream into messages: with a delimiter it supplies
 * itself, or with a framing its integration provides in code. Parts the request left out stay null,
 * so the creation can reject each with the field it concerns.
 */
public sealed interface TcpFramingSelection {

  /** The request field the selection is read from. */
  String FIELD = "framing";

  /**
   * A delimiter the connection supplies itself, kept exactly as received.
   *
   * @param mode the delimiter mode's name, such as {@code delimiterInclude}
   * @param delimiter the delimiter's characters, absent for a mode that takes none
   */
  record Custom(String mode, String delimiter) implements TcpFramingSelection {

    public static final String MODE_FIELD = FIELD + ".mode";
    public static final String DELIMITER_FIELD = FIELD + ".delimiter";
  }

  /**
   * A framing the connection's integration provides in code.
   *
   * @param framingId the framing's identifier
   */
  record Integration(UUID framingId) implements TcpFramingSelection {

    public static final String FRAMING_ID_FIELD = FIELD + ".framingId";
  }
}
