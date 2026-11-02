// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.service;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.ports.gateway.kafka.KafkaProvisioningService;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.api.general.technical.gateway.kafka.ByodSourcePublisher;
import com.opencop.core.sdk.model.connection.spec.ConnectionSpec;
import com.opencop.core.sdk.model.connection.spec.security.NoSecurity;
import com.opencop.core.sdk.model.connection.spec.transport.TcpConfig;
import com.opencop.core.sdk.model.datasource.AccessMode;
import com.opencop.core.sdk.model.datasource.ByodSourceState;
import com.opencop.core.sdk.model.datasource.KafkaDataSource;
import com.opencop.core.sdk.model.datasource.SocketDataSource;
import com.opencop.core.sdk.technical.gateway.broker.kafka.MessageBrokerProvider;
import com.opencop.core.sdk.technical.gateway.broker.kafka.properties.LocalProviderProperties;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ByodReconciliationServiceTest {

  private static final String PROVIDER = MessageBrokerProvider.LOCAL.name();

  private DataSourceRepository repository;
  private ByodSourcePublisher publisher;
  private KafkaProvisioningService provisioner;
  private ByodReconciliationService service;

  @BeforeEach
  void setUp() {
    repository = mock(DataSourceRepository.class);
    publisher = mock(ByodSourcePublisher.class);
    provisioner = mock(KafkaProvisioningService.class);
    var providerConfig = new LocalProviderProperties("", "", 1048576, 10);
    service = new ByodReconciliationService(repository, publisher, provisioner, providerConfig);
  }

  @Test
  void enabledSources_publishStarted() {
    var kafka = kafkaSource(true);
    when(repository.findAll(PROVIDER)).thenReturn(List.of(kafka));

    service.reconcile();

    verify(publisher)
        .publish(
            argThat(
                s ->
                    s.sourceId().equals(kafka.id())
                        && s.status() == ByodSourceState.Status.STARTED));
    verifyNoInteractions(provisioner);
  }

  @Test
  void disabledSources_removeAclAndPublishStopped() {
    var kafka = kafkaSource(false);
    when(repository.findAll(PROVIDER)).thenReturn(List.of(kafka));

    service.reconcile();

    verify(provisioner).removeWriteAcl(kafka.username(), kafka.topicName());
    verify(publisher)
        .publish(
            argThat(
                s ->
                    s.sourceId().equals(kafka.id())
                        && s.status() == ByodSourceState.Status.STOPPED));
  }

  @Test
  void mixedSources_handlesEachCorrectly() {
    var enabled = kafkaSource(true);
    var disabled = kafkaSource(false);
    when(repository.findAll(PROVIDER)).thenReturn(List.of(enabled, disabled));

    service.reconcile();

    verify(publisher)
        .publish(
            argThat(
                s ->
                    s.sourceId().equals(enabled.id())
                        && s.status() == ByodSourceState.Status.STARTED));
    verify(publisher)
        .publish(
            argThat(
                s ->
                    s.sourceId().equals(disabled.id())
                        && s.status() == ByodSourceState.Status.STOPPED));
    verify(provisioner).removeWriteAcl(disabled.username(), disabled.topicName());
  }

  @Test
  void socketSources_areIgnored() {
    var socket =
        new SocketDataSource(
            UUID.randomUUID(),
            "tak-server",
            "default",
            new ConnectionSpec(
                new TcpConfig("127.0.0.1", 8087, null, null, null, null, new NoSecurity())),
            null,
            null,
            null,
            AccessMode.READ,
            true,
            null,
            null,
            null,
            Instant.now(),
            null,
            Instant.now(),
            null,
            null,
            true);
    when(repository.findAll(PROVIDER)).thenReturn(List.of(socket));

    service.reconcile();

    verifyNoInteractions(publisher);
    verifyNoInteractions(provisioner);
  }

  @Test
  void noSources_isNoOp() {
    when(repository.findAll(PROVIDER)).thenReturn(List.of());

    service.reconcile();

    verify(publisher, never()).publish(argThat(s -> true));
    verifyNoInteractions(provisioner);
  }

  private static KafkaDataSource kafkaSource(boolean enabled) {
    var id = UUID.randomUUID();
    return new KafkaDataSource(
        id,
        "byod-" + id.toString().substring(0, 8),
        "default",
        "opencop.byod.test." + id,
        "byod-user-" + id,
        null,
        null,
        AccessMode.READ,
        enabled,
        null,
        null,
        Instant.now(),
        null,
        Instant.now(),
        null,
        null,
        PROVIDER,
        true);
  }
}
