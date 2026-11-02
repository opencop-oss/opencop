// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.integration.builtin.business.policy.rtsp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.core.sdk.ports.integration.connection.input.ConnectionInputException;
import com.opencop.core.sdk.ports.integration.connection.input.ConnectionInputFailure;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class RtspUrlPolicyTest {

  @ParameterizedTest
  @ValueSource(strings = {"rtsp://camera.example.test/live", "rtsps://camera.example.test:322/s"})
  void acceptsAnRtspStreamUrl(String url) {
    assertThatCode(() -> RtspUrlPolicy.requireValid(url)).doesNotThrowAnyException();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = " ")
  void rejectsAMissingUrl(String url) {
    assertRejected(url, RtspUrlPolicy.MISSING_URL, "RTSP URL must not be blank");
  }

  /** Each reason keeps the message v1 clients already receive. */
  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "rtsp://camera example.test/live|INVALID_RTSP_URL|Invalid RTSP URL",
        "http://camera.example.test/live|UNSUPPORTED_RTSP_SCHEME"
            + "|RTSP URL must use rtsp:// or rtsps:// scheme",
        "rtsp:///live|MISSING_RTSP_HOST|RTSP URL must contain a host",
        "rtsp://user:pass@camera.example.test/live|RTSP_URL_CREDENTIALS"
            + "|RTSP URL must not contain credentials — use Vault instead",
        "rtsp://camera.example.test/live?token=1|RTSP_URL_QUERY"
            + "|RTSP URL must not contain query parameters"
      })
  void rejectsAUrlAnRtspConnectionCannotStore(String url, String detailCode, String message) {
    assertRejected(url, detailCode, message);
  }

  private static void assertRejected(String url, String detailCode, String message) {
    assertThatThrownBy(() -> RtspUrlPolicy.requireValid(url))
        .isInstanceOfSatisfying(
            ConnectionInputException.class,
            exception -> {
              assertThat(exception.failure()).isEqualTo(ConnectionInputFailure.INVALID_INPUT);
              assertThat(exception.providerDetailCode()).contains(detailCode);
              assertThat(exception.field()).contains(RtspUrlPolicy.URL_FIELD);
              assertThat(exception.getMessage()).isEqualTo(message);
            });
  }
}
