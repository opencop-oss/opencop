// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.model.serializer;

import java.nio.charset.StandardCharsets;

public class StringSerializer implements Serializer<String, byte[]> {
  @Override
  public byte[] serialize(String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }

  @Override
  public String deserialize(byte[] serialized) {
    if (serialized == null) return null;
    return new String(serialized, StandardCharsets.UTF_8);
  }
}
