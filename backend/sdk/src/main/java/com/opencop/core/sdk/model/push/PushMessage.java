// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.push;

import java.util.List;
import java.util.UUID;

public record PushMessage(UUID sourceId, List<byte[]> payload) {
  static final String CHANNEL_PREFIX = "opencop:connect:";
  static final String CHANNEL_SUFFIX = ":push";

  public static String channelFor(String instanceId) {
    return CHANNEL_PREFIX + instanceId + CHANNEL_SUFFIX;
  }
}
