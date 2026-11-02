// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.connection.spec.transport;

import com.opencop.core.sdk.business.model.profile.DelimiterModes;
import java.util.Objects;

/** A delimiter stored on the connection itself. */
public record CustomTcpFraming(DelimiterModes mode, String delimiter) implements TcpFraming {
  static final String KIND = "custom";

  public CustomTcpFraming {
    Objects.requireNonNull(mode, "mode");
  }
}
