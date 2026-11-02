// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.watchbox.ports.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class WatchboxCallerTest {

  @Test
  void actorIdentityFields_whenValuesArePresent_areSanitizedAndBounded() {
    var caller =
        new WatchboxCaller(
            UUID.randomUUID(), false, "  Audit\nActor\u0000  ", "\t audit.actor@example.test\r");

    assertThat(caller.fullName()).isEqualTo("AuditActor");
    assertThat(caller.email()).isEqualTo("audit.actor@example.test");

    var bounded = new WatchboxCaller(UUID.randomUUID(), false, "😀".repeat(300), "e".repeat(400));
    assertThat(bounded.fullName().codePointCount(0, bounded.fullName().length())).isEqualTo(255);
    assertThat(bounded.email()).hasSize(320);

    var absent = new WatchboxCaller(UUID.randomUUID(), false, " \n ", null);
    assertThat(absent.fullName()).isNull();
    assertThat(absent.email()).isNull();
  }
}
