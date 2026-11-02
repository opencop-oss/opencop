// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.connection.extension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.sdk.ports.gateway.secrets.SecretStore;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VaultExtensionSecretGatewayTest {

  private static final UUID SOURCE_ID = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
  private static final UUID INTEGRATION_ID =
      UUID.fromString("0f7b8e7c-49b8-4d0e-8f43-4bb1b0f0a1c2");
  private static final UUID OTHER_INTEGRATION_ID =
      UUID.fromString("6c1f0f6e-0c38-4a5b-9c0e-3f4d2b8a7e91");
  private static final String EXTENSION_ID = "data-sync";
  private static final String PATH =
      "opencop/shared/connect/" + SOURCE_ID + "/extensions/" + INTEGRATION_ID + "/" + EXTENSION_ID;

  private final SecretStore secretStore = mock(SecretStore.class);
  private final VaultExtensionSecretGateway gateway = new VaultExtensionSecretGateway(secretStore);

  @Test
  void keepsTheEntryUnderAPathOfItsConnectionIntegrationAndExtension() {
    when(secretStore.read(PATH)).thenReturn(Map.of("token", "t"));

    assertThat(gateway.read(SOURCE_ID, INTEGRATION_ID, EXTENSION_ID))
        .isEqualTo(Map.of("token", "t"));
  }

  @Test
  void anotherIntegrationsExtensionOfTheSameNameNeverReadsTheEntry() {
    when(secretStore.read(PATH)).thenReturn(Map.of("token", "t"));

    assertThat(gateway.read(SOURCE_ID, OTHER_INTEGRATION_ID, EXTENSION_ID)).isEmpty();
  }

  @Test
  void aMissingEntryReadsAsEmpty() {
    when(secretStore.read(PATH)).thenReturn(null);

    assertThat(gateway.read(SOURCE_ID, INTEGRATION_ID, EXTENSION_ID)).isEmpty();
  }

  @Test
  void replacingDropsTheSecretsTheNewDocumentNoLongerHolds() {
    when(secretStore.read(PATH)).thenReturn(Map.of("token", "old", "stale", "x"));

    gateway.replace(SOURCE_ID, INTEGRATION_ID, EXTENSION_ID, Map.of("token", "new"));

    verify(secretStore).patch(PATH, Map.of("token", "new"), Set.of("stale"));
  }

  @Test
  void deletingRemovesEveryVersionOfTheEntry() {
    gateway.delete(SOURCE_ID, INTEGRATION_ID, EXTENSION_ID);

    verify(secretStore).deleteAllVersions(PATH);
  }
}
