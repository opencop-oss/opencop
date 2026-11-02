// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.connection.setup;

import com.opencop.core.sdk.business.model.profile.DelimiterModes;
import java.util.Objects;
import java.util.UUID;

/**
 * How a resolved TCP connection splits its stream into messages: with a delimiter it supplies
 * itself, or with a framing its integration provides in code.
 */
public sealed interface ResolvedTcpFraming {

  /**
   * A delimiter the connection supplies itself, kept exactly as received.
   *
   * @param mode how the stream is split on the delimiter
   * @param delimiter the delimiter's characters, empty for a mode that splits on none
   */
  record Custom(DelimiterModes mode, String delimiter) implements ResolvedTcpFraming {

    public Custom {
      Objects.requireNonNull(mode, "mode");
    }
  }

  /**
   * A framing the connection's integration provides in code.
   *
   * @param framingId the framing's identifier
   */
  record Integration(UUID framingId) implements ResolvedTcpFraming {

    public Integration {
      Objects.requireNonNull(framingId, "framingId");
    }
  }
}
