// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.connection.extension;

import java.util.Map;
import java.util.Objects;

/**
 * The body an extension operation takes or returns. The host reads and writes it; the handler only
 * sees the value.
 *
 * @param <T> the type of the body's value
 */
public sealed interface ExtensionBody<T> {

  /** No body: a request without one, or a response answered with 204. */
  static ExtensionBody<Void> none() {
    return None.INSTANCE;
  }

  /**
   * A JSON body read into, or written from, a value of the type. A primitive type stands for its
   * wrapper, since a body's value is always an object.
   *
   * @throws IllegalArgumentException for {@code void.class}: an operation without a body declares
   *     {@link #none()}
   */
  static <T> ExtensionBody<T> json(Class<T> type) {
    return new Json<>(type);
  }

  /** No body. */
  enum None implements ExtensionBody<Void> {
    INSTANCE
  }

  /** A JSON body bound to its value's type. */
  record Json<T>(Class<T> type) implements ExtensionBody<T> {

    private static final Map<Class<?>, Class<?>> WRAPPERS =
        Map.of(
            boolean.class, Boolean.class,
            byte.class, Byte.class,
            char.class, Character.class,
            short.class, Short.class,
            int.class, Integer.class,
            long.class, Long.class,
            float.class, Float.class,
            double.class, Double.class);

    @SuppressWarnings("unchecked")
    public Json {
      Objects.requireNonNull(type, "type");
      if (type == void.class) {
        throw new IllegalArgumentException("A body without content is declared as none()");
      }
      type = (Class<T>) WRAPPERS.getOrDefault(type, type);
    }
  }
}
