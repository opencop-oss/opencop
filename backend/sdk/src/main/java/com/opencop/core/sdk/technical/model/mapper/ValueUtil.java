// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.model.mapper;

import java.util.ArrayList;
import java.util.List;
import opencop.core.proto.deckgl.ElementProtos;
import opencop.core.proto.deckgl.ElementProtos.Value;

public class ValueUtil {

  private ValueUtil() {}

  public static Value valueFrom(String s) {
    return Value.newBuilder().setString(s).build();
  }

  public static Value valueFrom(double n) {
    return Value.newBuilder().setDouble(n).build();
  }

  public static Value valueFromDoubles(List<Double> list) {
    return Value.newBuilder()
        .setDoubleArr(ElementProtos.RepeatedDouble.newBuilder().addAllNumbers(list))
        .build();
  }

  public static Value valueFrom(List<? extends Number> list) {
    if (list == null || list.isEmpty()) {
      throw new ProtobufException("List is empty");
    }

    Number first = list.getFirst();

    return switch (first) {
      case Integer i -> valueFromInts(castList(list, Integer.class));
      case Float f -> valueFromFloats(castList(list, Float.class));
      case Double d -> valueFromDoubles(castList(list, Double.class));
      case Long l -> valueFromLongs(castList(list, Long.class));
      default -> throw new ProtobufException("List is not a list of supported numbers");
    };
  }

  private static <T extends Number> List<T> castList(List<? extends Number> list, Class<T> clazz) {
    // Validate *all* elements are of the same type
    if (!list.stream().allMatch(clazz::isInstance)) {
      throw new ProtobufException("List contains mixed or invalid element types");
    }

    // Now safely cast
    return list.stream().map(clazz::cast).toList();
  }

  public static Value valueFrom(boolean b) {
    return Value.newBuilder().setBool(b).build();
  }

  public static Value valueFrom(int i) {
    return Value.newBuilder().setInt32(i).build();
  }

  public static Value valueFromInts(List<Integer> list) {
    return Value.newBuilder()
        .setInt32Arr(ElementProtos.RepeatedInt32.newBuilder().addAllNumbers(list))
        .build();
  }

  public static Value valueFrom(long l) {
    return Value.newBuilder().setInt64(l).build();
  }

  public static Value valueFromLongs(List<Long> list) {
    return Value.newBuilder()
        .setInt64Arr(ElementProtos.RepeatedInt64.newBuilder().addAllNumbers(list))
        .build();
  }

  public static Value valueFrom(float f) {
    return Value.newBuilder().setFloat(f).build();
  }

  public static Value valueFromFloats(List<Float> list) {
    return Value.newBuilder()
        .setFloatArr(ElementProtos.RepeatedFloat.newBuilder().addAllNumbers(list))
        .build();
  }

  public static Value valueFrom(Object o) {
    return switch (o) {
      case String s -> valueFrom(s);
      case Double d -> valueFrom((double) d);
      case Boolean b -> valueFrom((boolean) b);
      case Integer i -> valueFrom((int) i);
      case Long l -> valueFrom((long) l);
      case Float f -> valueFrom((float) f);
      case List<?> l -> valueFromList(l);
      default -> throw new ProtobufException("Input is not primitive or it's boxed counterpart.");
    };
  }

  private static Value valueFromList(List<?> list) {
    if (list.isEmpty()) {
      throw new ProtobufException("List is empty");
    }

    Object first = list.getFirst();

    return switch (first) {
      case Integer ignored -> valueFromInts(toTypedList(list, Integer.class));
      case Long ignored -> valueFromLongs(toTypedList(list, Long.class));
      case Float ignored -> valueFromFloats(toTypedList(list, Float.class));
      case Double ignored -> valueFromDoubles(toTypedList(list, Double.class));
      default -> throw new ProtobufException("List is not a list of numbers");
    };
  }

  private static <T> List<T> toTypedList(List<?> list, Class<T> type) {
    List<T> result = new ArrayList<>(list.size());
    for (Object o : list) {
      if (!type.isInstance(o)) {
        throw new ProtobufException(
            "List contains mixed types. Expected "
                + type.getSimpleName()
                + " but found "
                + (o == null ? "null" : o.getClass().getSimpleName()));
      }
      result.add(type.cast(o));
    }
    return result;
  }
}
