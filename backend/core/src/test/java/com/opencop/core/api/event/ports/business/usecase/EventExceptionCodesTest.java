// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.ports.business.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class EventExceptionCodesTest {

  @Test
  void codes_areUnique() {
    var codes = Arrays.stream(EventExceptionCodes.values()).map(EventExceptionCodes::code).toList();

    assertThat(codes).doesNotHaveDuplicates();
  }
}
