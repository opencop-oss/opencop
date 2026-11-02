// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.model.serializer;

import com.google.protobuf.GeneratedMessage;
import com.opencop.core.sdk.technical.model.mapper.ProtobufMapper;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.springframework.core.serializer.support.SerializationFailedException;

public class ProtobufSerializer<I, O extends GeneratedMessage> implements Serializer<I, byte[]> {
  private final ProtobufMapper<I, O> mapper;
  private final Class<O> clazz;

  public ProtobufSerializer(ProtobufMapper<I, O> mapper, Class<O> clazz) {
    this.mapper = mapper;
    this.clazz = clazz;
  }

  @Override
  public byte[] serialize(I value) {
    return this.mapper.toMessage(value).toByteArray();
  }

  public I deserialize(byte[] serialized) {
    try {
      Method parseFromMethod = clazz.getMethod("parseFrom", byte[].class);

      Object parsed = parseFromMethod.invoke(null, (Object) serialized);
      O message = clazz.cast(parsed);

      return mapper.fromMessage(message);
    } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
      throw new SerializationFailedException(e.getMessage(), e);
    }
  }
}
