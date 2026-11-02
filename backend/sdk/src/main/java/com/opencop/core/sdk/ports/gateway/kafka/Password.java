// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.gateway.kafka;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Newtype wrapper providing type safety for password values.
 *
 * <p>Prevents accidental misuse such as logging, concatenation, or parameter mix-ups. Uses {@link
 * JsonValue} so Jackson serializes it as a plain string (not {@code {"value":"..."}}).
 *
 * <p>Overrides {@code toString()} to prevent password leaking in logs — records auto-generate
 * {@code toString()} that includes all fields.
 *
 * <p>Inspired by Rust's {@code secrecy} crate, which wraps sensitive values in {@code Secret<T>}
 * and zeros backing memory on {@code Drop}. Java cannot replicate the memory-zeroing guarantee:
 * there is no deterministic destruction, and this password is serialized as a JSON response —
 * creating immutable {@code String} copies in Jackson and servlet buffers that persist in heap
 * until GC. Unlike short-lived serverless containers, our services are long-running, so these
 * copies can linger. The security boundary for the response payload is TLS on the wire; in-memory
 * exposure is an accepted limitation of the JVM.
 *
 * @param value the base64url-encoded password string
 */
public record Password(@JsonValue String value) {

  @Override
  public String toString() {
    return "Password[REDACTED]";
  }
}
