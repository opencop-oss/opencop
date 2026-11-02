// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.gateway.socket.rtsp;

import java.util.regex.Pattern;

public final class RtspUrlRedactor {

  public static final String REDACTED_RTSP_URL = "<redacted-rtsp-url>";

  private static final Pattern RTSP_URL_PATTERN =
      Pattern.compile("(?i)rtsps?://[^\\s,;\"'()\\[\\]]+");

  private RtspUrlRedactor() {}

  public static String redactMessage(String message) {
    if (message == null || message.isBlank()) {
      return message;
    }

    return RTSP_URL_PATTERN.matcher(message).replaceAll(REDACTED_RTSP_URL);
  }

  public static String redactUrl(String url) {
    if (url == null || url.isBlank()) {
      return url;
    }

    return REDACTED_RTSP_URL;
  }

  public static RuntimeException redactException(Throwable throwable) {
    if (throwable == null) {
      return new RuntimeException();
    }

    var redacted =
        new RuntimeException(
            redactMessage(throwable.getMessage()),
            throwable.getCause() == null ? null : redactException(throwable.getCause()));
    redacted.setStackTrace(throwable.getStackTrace());
    for (var suppressed : throwable.getSuppressed()) {
      redacted.addSuppressed(redactException(suppressed));
    }
    return redacted;
  }
}
