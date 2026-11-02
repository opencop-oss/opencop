// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.model.mapper;

import com.google.protobuf.ByteString;
import java.util.List;
import opencop.core.proto.websocket.SocketProtos.SocketMessage;

public class MultiPayloadMapper
    implements ProtobufMapper<List<byte[]>, SocketMessage.MultiPayload> {
  @Override
  public SocketMessage.MultiPayload toMessage(List<byte[]> input) {
    return SocketMessage.MultiPayload.newBuilder()
        .addAllPayload(input.stream().map(ByteString::copyFrom).toList())
        .build();
  }

  @Override
  public List<byte[]> fromMessage(SocketMessage.MultiPayload payload) {
    return payload.getPayloadList().stream().map(ByteString::toByteArray).toList();
  }
}
