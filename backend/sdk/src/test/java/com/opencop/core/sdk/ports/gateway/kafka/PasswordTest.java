// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.gateway.kafka;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PasswordTest {

  @Test
  void toStringDoesNotLeakValue() {
    var password = new Password("super-secret-credential");

    assertFalse(password.toString().contains("super-secret-credential"));
    assertEquals("Password[REDACTED]", password.toString());
  }

  @Test
  void valueAccessorReturnsPlaintext() {
    var password = new Password("super-secret-credential");

    assertEquals("super-secret-credential", password.value());
  }

  @Test
  void credentialsToStringDoesNotLeakPassword() {
    var creds =
        new KafkaCredentials(
            "opencop.byod.test",
            "byod-test",
            new Password("super-secret-credential"),
            "localhost:9094");

    assertFalse(creds.toString().contains("super-secret-credential"));
  }
}
