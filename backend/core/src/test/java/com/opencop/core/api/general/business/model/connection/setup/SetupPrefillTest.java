// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.connection.setup;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.sdk.business.model.integration.connection.input.ConnectionInputValue;
import com.opencop.core.sdk.business.model.integration.connection.input.DeclaredConnectionInput;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.ExtractorDescriptor;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.FileField;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.SecretField;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.TextField;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SetupPrefillTest {

  private static final UUID EXTRACTOR_ID = UUID.fromString("00000000-0000-4000-8000-0000000000e1");
  private static final ExtractorDescriptor DESCRIPTOR =
      new ExtractorDescriptor(
          EXTRACTOR_ID,
          "Certificate",
          List.of(
              new TextField("host", "Host", true),
              new SecretField("hostPassword", "Password", true),
              new TextField("port", "Port", true),
              new FileField("keyStore", "Key store", true, Set.of(".p12"), Set.of()),
              new TextField("label", "Label", false)));

  /** A secret is left out for its declared kind, even when its name reads like a text field. */
  @Test
  void keepsTheValuesOfTheFieldsTheExtractorDeclaresAsText() {
    var input =
        new DeclaredConnectionInput(
            List.of(
                new ConnectionInputValue("port", "8089"),
                new ConnectionInputValue("hostPassword", "secret"),
                new ConnectionInputValue("host", "tak.example.test")),
            List.of());

    var prefill = SetupPrefill.of(DESCRIPTOR, input);

    assertThat(prefill.extractorId()).isEqualTo(EXTRACTOR_ID);
    assertThat(prefill.values()).isEqualTo(Map.of("host", "tak.example.test", "port", "8089"));
  }

  @Test
  void leavesOutATextFieldTheSetupLeftEmpty() {
    var input =
        new DeclaredConnectionInput(
            List.of(new ConnectionInputValue("host", "tak.example.test")), List.of());

    assertThat(SetupPrefill.of(DESCRIPTOR, input).values()).containsOnlyKeys("host");
  }
}
