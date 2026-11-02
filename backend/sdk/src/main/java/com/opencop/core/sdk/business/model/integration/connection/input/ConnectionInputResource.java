// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.connection.input;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/** Framework-neutral metadata and repeatable access to one supplied connection input. */
public final class ConnectionInputResource {

  private final String role;
  private final String filename;
  private final String mediaType;
  private final long byteSize;
  private final ContentOpener contentOpener;

  public ConnectionInputResource(
      String role, String filename, String mediaType, long byteSize, ContentOpener contentOpener) {
    this.role = requireText(role, "role");
    this.filename = requireText(filename, "filename");
    this.mediaType = requireText(mediaType, "mediaType");
    if (byteSize < 0) {
      throw new IllegalArgumentException("byteSize must not be negative");
    }
    this.byteSize = byteSize;
    this.contentOpener = Objects.requireNonNull(contentOpener, "contentOpener");
  }

  public String role() {
    return role;
  }

  public String filename() {
    return filename;
  }

  public String mediaType() {
    return mediaType;
  }

  public long byteSize() {
    return byteSize;
  }

  public InputStream openContent() throws IOException {
    return Objects.requireNonNull(contentOpener.open(), "contentOpener result");
  }

  @Override
  public String toString() {
    return "ConnectionInputResource[role=%s, filename=%s, mediaType=%s, byteSize=%d]"
        .formatted(role, filename, mediaType, byteSize);
  }

  private static String requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value;
  }

  @FunctionalInterface
  public interface ContentOpener {

    InputStream open() throws IOException;
  }
}
