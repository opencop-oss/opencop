// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.mapper;

import com.opencop.core.sdk.ports.mapper.EventMapper;
import java.util.UUID;

public class EventMapperMock<I, O> implements EventMapper<I, O> {

  private final ToOpenCopBehavior<I, O> toBehavior;
  private final FromOpenCopBehavior<I, O> fromBehavior;

  public EventMapperMock(
      ToOpenCopBehavior<I, O> toBehavior, FromOpenCopBehavior<I, O> fromBehavior) {
    this.toBehavior = toBehavior;
    this.fromBehavior = fromBehavior;
  }

  @Override
  public O toOpenCopModel(UUID serverId, I rawInput) {
    return toBehavior.run(this, rawInput);
  }

  @Override
  public I fromOpenCopModel(O opencopModel) {
    return fromBehavior.run(this, opencopModel);
  }

  @FunctionalInterface
  public interface ToOpenCopBehavior<I, O> {
    O run(EventMapperMock<I, O> mock, I input);
  }

  @FunctionalInterface
  public interface FromOpenCopBehavior<I, O> {
    I run(EventMapperMock<I, O> mock, O input);
  }

  public static <I, O> ToOpenCopBehavior<I, O> toBehavior(O output) {
    return (_, _) -> output;
  }

  public static <I, O> FromOpenCopBehavior<I, O> fromBehavior(I output) {
    return (_, _) -> output;
  }
}
