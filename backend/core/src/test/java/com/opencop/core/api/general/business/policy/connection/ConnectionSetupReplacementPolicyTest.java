// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.policy.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.core.api.general.ports.usecase.connection.ConnectionExceptionCodes;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionManagementException;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.model.connection.spec.ConnectionSpec;
import com.opencop.core.sdk.model.connection.spec.security.KeystoreRef;
import com.opencop.core.sdk.model.connection.spec.security.MtlsConfig;
import com.opencop.core.sdk.model.connection.spec.security.NoSecurity;
import com.opencop.core.sdk.model.connection.spec.transport.TcpConfig;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ConnectionSetupReplacementPolicyTest {

  private static final IntegrationConnectionSchemaReference CURRENT =
      new IntegrationConnectionSchemaReference(
          UUID.fromString("0f7b8e7c-49b8-4d0e-8f43-4bb1b0f0a1c2"), 1);
  private static final IntegrationConnectionSchemaReference NEXT_VERSION =
      new IntegrationConnectionSchemaReference(CURRENT.integrationId(), 2);
  private static final IntegrationConnectionSchemaReference OTHER_INTEGRATION =
      new IntegrationConnectionSchemaReference(
          UUID.fromString("6c1f0f6e-0c38-4a5b-9c0e-3f4d2b8a7e91"), 1);

  private static final String OLD_CLIENT_REF = "secrets/old/client";
  private static final String NEW_CLIENT_REF = "secrets/new/client";
  private static final String TRUSTSTORE_REF = "secrets/truststore";

  /** A rotation keeps a store it still reads and leaves behind only the ones it replaced. */
  @Test
  void retiresOnlyTheSecretsTheReplacementNoLongerReads() {
    var replaced = mtls(OLD_CLIENT_REF, TRUSTSTORE_REF);

    assertThat(
            ConnectionSetupReplacementPolicy.retiredSecretRefs(
                replaced, mtls(NEW_CLIENT_REF, TRUSTSTORE_REF)))
        .containsExactly(OLD_CLIENT_REF);
    assertThat(ConnectionSetupReplacementPolicy.retiredSecretRefs(replaced, replaced)).isEmpty();
  }

  @Test
  void retiresEverySecretWhenTheReplacementReadsNone() {
    assertThat(
            ConnectionSetupReplacementPolicy.retiredSecretRefs(
                mtls(OLD_CLIENT_REF, TRUSTSTORE_REF),
                new ConnectionSpec(
                    new TcpConfig(
                        "relay.example.test", 9090, null, null, null, null, new NoSecurity()))))
        .containsExactly(OLD_CLIENT_REF, TRUSTSTORE_REF);
  }

  private static ConnectionSpec mtls(String clientRef, String truststoreRef) {
    return new ConnectionSpec(
        new TcpConfig(
            "relay.example.test",
            9090,
            null,
            null,
            null,
            null,
            new MtlsConfig(
                null,
                null,
                true,
                new KeystoreRef(truststoreRef, "PKCS12"),
                new KeystoreRef(clientRef, "PKCS12"))));
  }

  @Test
  void aConnectionWithRecordsCannotMoveToAnotherIntegration() {
    assertThatThrownBy(
            () ->
                ConnectionSetupReplacementPolicy.assertRecordsKept(
                    CURRENT, OTHER_INTEGRATION, true))
        .isInstanceOfSatisfying(
            ConnectionManagementException.class,
            error -> {
              assertThat(error.getCode())
                  .isEqualTo(ConnectionExceptionCodes.CONNECTION_RECORDS_PRESENT.code());
              assertThat(error.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
            });
  }

  @Test
  void recordsSurviveAnUpgradeWithinTheIntegration() {
    assertThatCode(
            () -> ConnectionSetupReplacementPolicy.assertRecordsKept(CURRENT, NEXT_VERSION, true))
        .doesNotThrowAnyException();
  }

  @Test
  void aConnectionWithoutRecordsMovesFreely() {
    assertThatCode(
            () ->
                ConnectionSetupReplacementPolicy.assertRecordsKept(
                    CURRENT, OTHER_INTEGRATION, false))
        .doesNotThrowAnyException();
    assertThatCode(
            () ->
                ConnectionSetupReplacementPolicy.assertRecordsKept(null, OTHER_INTEGRATION, false))
        .doesNotThrowAnyException();
  }
}
