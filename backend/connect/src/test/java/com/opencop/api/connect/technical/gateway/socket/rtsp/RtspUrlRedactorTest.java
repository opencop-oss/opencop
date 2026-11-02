// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.gateway.socket.rtsp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;

class RtspUrlRedactorTest {

  @Test
  void redactsWholeUrl() {
    assertThat(RtspUrlRedactor.redactUrl("rtsp://user:pass@example.com/path"))
        .isEqualTo(RtspUrlRedactor.REDACTED_RTSP_URL);
  }

  @Test
  void redactsWholeUrlWhenQueryValuesArePresent() {
    assertThat(RtspUrlRedactor.redactUrl("rtsp://example.com/path?token=abc&camera=one"))
        .isEqualTo(RtspUrlRedactor.REDACTED_RTSP_URL);
  }

  @Test
  void redactsUrlsInsideExceptionMessages() {
    var message =
        "Could not open input rtsp://user:pass@example.com:8554/path?token=abc while probing";

    assertThat(RtspUrlRedactor.redactMessage(message))
        .isEqualTo("Could not open input <redacted-rtsp-url> while probing");
  }

  @Test
  void fallsBackOnMalformedUrls() {
    assertThat(RtspUrlRedactor.redactUrl("rtsp://user:pass@exa mple/path?token=abc"))
        .isEqualTo(RtspUrlRedactor.REDACTED_RTSP_URL);
  }

  @Test
  void redactsUrlDetailsFromExceptionWhilePreservingNonSensitiveContent() {
    var throwable =
        new RuntimeException(
            "Could not open rtsp://user:pass@example.com:8554/path?token=secret",
            new IllegalArgumentException("nested rtsps://example.com/private?password=secret"));

    var redacted = RtspUrlRedactor.redactException(throwable);

    assertThat(redacted.getMessage()).isEqualTo("Could not open <redacted-rtsp-url>");
    assertThat(redacted.getCause()).isNotNull();
    assertThat(redacted.getCause().getMessage()).isEqualTo("nested <redacted-rtsp-url>");
    assertThat(stackTrace(redacted))
        .doesNotContain("rtsp://")
        .doesNotContain("rtsps://")
        .doesNotContain("example.com")
        .doesNotContain("password")
        .doesNotContain("secret");
  }

  private static String stackTrace(Throwable throwable) {
    var writer = new StringWriter();
    throwable.printStackTrace(new PrintWriter(writer));
    return writer.toString();
  }
}
