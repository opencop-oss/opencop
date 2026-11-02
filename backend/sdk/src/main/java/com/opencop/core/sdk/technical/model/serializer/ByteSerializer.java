// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.model.serializer;

public class ByteSerializer implements Serializer<byte[], byte[]> {
  @Override
  public byte[] serialize(byte[] value) {
    return value;
  }

  @Override
  public byte[] deserialize(byte[] serialized) {
    return serialized;
  }
}
