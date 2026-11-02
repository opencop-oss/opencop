// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.connection.input;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ConnectionInputResourceTest {

  @Test
  void ownsMetadataAndOpensFreshContentViewsOnDemand() throws Exception {
    var opened = new AtomicInteger();
    var resource =
        new ConnectionInputResource(
            "package",
            "tak.zip",
            "application/zip",
            3,
            () -> {
              opened.incrementAndGet();
              return new ByteArrayInputStream(new byte[] {1, 2, 3});
            });

    assertThat(resource.role()).isEqualTo("package");
    assertThat(resource.filename()).isEqualTo("tak.zip");
    assertThat(resource.mediaType()).isEqualTo("application/zip");
    assertThat(resource.byteSize()).isEqualTo(3);
    assertThat(opened).hasValue(0);
    assertThat(resource.openContent().readAllBytes()).containsExactly(1, 2, 3);
    assertThat(resource.openContent().readAllBytes()).containsExactly(1, 2, 3);
    assertThat(opened).hasValue(2);
  }

  @Test
  void rejectsIncompleteOrInvalidMetadataAndOpeners() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new ConnectionInputResource(
                    " ", "tak.zip", "application/zip", 1, InputStream::nullInputStream));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new ConnectionInputResource(
                    "package", " ", "application/zip", 1, InputStream::nullInputStream));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new ConnectionInputResource(
                    "package", "tak.zip", " ", 1, InputStream::nullInputStream));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new ConnectionInputResource(
                    "package", "tak.zip", "application/zip", -1, InputStream::nullInputStream));
    assertThatNullPointerException()
        .isThrownBy(
            () -> new ConnectionInputResource("package", "tak.zip", "application/zip", 1, null));
  }
}
