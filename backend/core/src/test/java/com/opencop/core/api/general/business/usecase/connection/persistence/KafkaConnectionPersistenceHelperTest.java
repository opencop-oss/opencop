// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.opencop.core.api.component.support.TestActors;
import com.opencop.core.api.general.business.model.Caller;
import com.opencop.core.api.general.business.model.connection.NewKafkaConnection;
import com.opencop.core.api.general.business.model.connection.access.ReadAccess;
import com.opencop.core.api.general.business.model.connection.setup.KafkaSetup;
import com.opencop.core.api.general.business.model.connection.setup.SetupPrefill;
import com.opencop.core.api.general.business.service.AmbiguousPersistenceOutcomeHandler;
import com.opencop.core.api.general.business.service.KafkaConnectionPersistenceService;
import com.opencop.core.api.general.ports.gateway.kafka.ByodSourceStatePublisher;
import com.opencop.core.api.general.ports.gateway.kafka.KafkaProvisioningService;
import com.opencop.core.sdk.business.model.integration.connection.material.kafka.ScramSecurityMaterial;
import com.opencop.core.sdk.integration.builtin.business.model.NativeConnectionSchema;
import com.opencop.core.sdk.model.datasource.ByodSourceState;
import com.opencop.core.sdk.model.datasource.KafkaDataSource;
import com.opencop.core.sdk.ports.gateway.kafka.KafkaCredentials;
import com.opencop.core.sdk.ports.gateway.kafka.Password;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class KafkaConnectionPersistenceHelperTest {

  private static final UUID SOURCE_ID = UUID.fromString("00000000-0000-4000-8000-0000000000b1");
  private static final UUID USER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000c1");
  private static final Caller ACTOR = TestActors.of(USER_ID);
  private static final UUID MAPPER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
  private static final String SOURCE_NAME = "Alpha Kafka";
  private static final String TOPIC = "opencop.byod.alpha";
  private static final String USERNAME = "byod-producer";
  private static final Instant COMMITTED_AT = Instant.parse("2026-10-02T12:00:00Z");
  private static final SetupPrefill PREFILL =
      new SetupPrefill(UUID.fromString("00000000-0000-4000-8000-0000000000d1"), Map.of());

  private final KafkaProvisioningService provisioner = mock(KafkaProvisioningService.class);
  private final KafkaConnectionPersistenceService persistence =
      mock(KafkaConnectionPersistenceService.class);
  private final AmbiguousPersistenceOutcomeHandler ambiguousOutcomes =
      mock(AmbiguousPersistenceOutcomeHandler.class);
  private final ByodSourceStatePublisher publisher = mock(ByodSourceStatePublisher.class);
  private final KafkaConnectionPersistenceHelper helper =
      new KafkaConnectionPersistenceHelper(
          provisioner, persistence, ambiguousOutcomes, publisher, "local");

  private final KafkaCredentials credentials =
      new KafkaCredentials(TOPIC, USERNAME, new Password("secret"), "localhost:9092");

  @Test
  void publishesTheCommittedConnectionAsStarted() {
    givenTheConnectionCommits();

    helper.provisionAndPersist(connection(PREFILL));

    var published = ArgumentCaptor.forClass(ByodSourceState.class);
    verify(publisher).publish(published.capture());
    assertThat(published.getValue().sourceId()).isEqualTo(SOURCE_ID);
    assertThat(published.getValue().status()).isEqualTo(ByodSourceState.Status.STARTED);
    assertThat(published.getValue().updatedAt()).isEqualTo(COMMITTED_AT);
  }

  /**
   * The credentials are shown only once and the connection is already durable, so a broker failure
   * while publishing must still answer with them; reconciliation publishes the connection later.
   */
  @Test
  void answersWithTheCredentialsWhenPublishingFailsAfterCommit() {
    givenTheConnectionCommits();
    doThrow(new RuntimeException("Failed to publish BYOD source state"))
        .when(publisher)
        .publish(any());

    var answered = helper.provisionAndPersist(connection(PREFILL));

    assertThat(answered).isEqualTo(credentials);
    verify(provisioner, never()).deprovision(anyString(), anyString(), any());
    verifyNoInteractions(ambiguousOutcomes);
  }

  /** A Kafka setup is stored with its prefill and no settings, which Kafka never takes. */
  @Test
  void storesAKafkaConnectionWithItsPrefill() {
    givenTheConnectionCommits();
    var prefill = new SetupPrefill(UUID.randomUUID(), Map.of("producerByteRate", "2048"));

    var answered = helper.provisionAndPersist(connection(prefill));

    assertThat(answered).isEqualTo(credentials);
    verify(persistence)
        .persistNewConnection(
            any(KafkaDataSource.class),
            eq(NativeConnectionSchema.V1_REFERENCE),
            eq(prefill),
            eq(ACTOR));
  }

  private void givenTheConnectionCommits() {
    when(provisioner.provision(SOURCE_ID, SOURCE_NAME, null)).thenReturn(credentials);
    when(persistence.persistNewConnection(
            any(KafkaDataSource.class), eq(NativeConnectionSchema.V1_REFERENCE), any(), any()))
        .thenReturn(COMMITTED_AT);
  }

  private static NewKafkaConnection connection(SetupPrefill prefill) {
    return new NewKafkaConnection(
        SOURCE_ID,
        SOURCE_NAME,
        ACTOR,
        new KafkaSetup(
            new ReadAccess(MAPPER_ID),
            NativeConnectionSchema.V1_REFERENCE,
            prefill,
            new ScramSecurityMaterial(null)));
  }
}
