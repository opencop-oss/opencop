// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.connection.material.certificate;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Objects;

/** Self-contained binary content with repeatable stream views and no request-lifetime ownership. */
public final class DetachedBinaryMaterial {

  private final byte[] bytes;

  public DetachedBinaryMaterial(byte[] bytes) {
    this.bytes = Objects.requireNonNull(bytes, "bytes").clone();
  }

  public long byteSize() {
    return bytes.length;
  }

  public InputStream openStream() {
    return new ByteArrayInputStream(bytes);
  }

  @Override
  public boolean equals(Object other) {
    return this == other
        || (other instanceof DetachedBinaryMaterial that && Arrays.equals(bytes, that.bytes));
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(bytes);
  }

  @Override
  public String toString() {
    return "DetachedBinaryMaterial[byteSize=%d]".formatted(bytes.length);
  }
}
