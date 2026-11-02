// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.setup;

import static com.opencop.core.api.support.integration.TestIntegrationCatalogs.selection;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.opencop.core.api.component.support.TestActors;
import com.opencop.core.api.general.business.model.Caller;
import com.opencop.core.api.general.business.model.connection.access.ReadAccess;
import com.opencop.core.api.general.business.model.connection.create.ConnectionCreation;
import com.opencop.core.api.general.business.model.connection.create.ConnectionCreationDetails;
import com.opencop.core.api.general.business.usecase.connection.create.MapperOnlyCreationPreparation;
import com.opencop.core.api.general.business.usecase.connection.create.tcp.TcpConnectionSetupResolver;
import com.opencop.core.api.general.business.usecase.connection.mapping.ConnectionMapperSelection;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionExceptionCodes;
import com.opencop.core.api.support.integration.TestIntegrationCatalogs;
import com.opencop.core.sdk.business.model.integration.connection.input.ConnectionInputValue;
import com.opencop.core.sdk.business.model.integration.connection.input.DeclaredConnectionInput;
import com.opencop.core.sdk.business.model.integration.connection.material.kafka.ScramSecurityMaterial;
import com.opencop.core.sdk.business.model.integration.mapping.egress.EgressMapperContributions;
import com.opencop.core.sdk.business.model.profile.MapperProfile;
import com.opencop.core.sdk.business.model.profile.MapperQualifier;
import com.opencop.core.sdk.business.registry.integration.connection.input.ConnectionExtractorRegistry;
import com.opencop.core.sdk.business.registry.integration.connection.mapping.ConnectionMappingRegistry;
import com.opencop.core.sdk.business.registry.integration.connection.settings.ConnectionSettingsRegistry;
import com.opencop.core.sdk.integration.builtin.business.model.NativeConnectionMappingContribution;
import com.opencop.core.sdk.integration.builtin.business.model.NativeConnectionSchema;
import com.opencop.core.sdk.integration.builtin.business.model.NativeIntegration;
import com.opencop.core.sdk.integration.builtin.business.policy.kafka.KafkaProducerByteRatePolicy;
import com.opencop.core.sdk.integration.builtin.technical.extractor.kafka.NativeKafkaProducerExtractor;
import com.opencop.core.sdk.integration.builtin.technical.extractor.tcp.NativePlainTcpExtractor;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.ports.gateway.mapper.MapperProfileService;
import com.opencop.core.sdk.ports.gateway.secrets.ConnectionStore;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class KafkaConnectionSetupResolutionTest {

  private static final UUID USER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000c1");
  private static final Caller ACTOR = TestActors.of(USER_ID);
  private static final UUID MAPPER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
  private static final NativeConnectionSchema NATIVE =
      new NativeConnectionSchema(new NativeIntegration());

  private final MapperProfileService mappers = mock(MapperProfileService.class);
  private final ConnectionSetupResolution resolution =
      new ConnectionSetupResolution(
          mock(TcpConnectionSetupResolver.class),
          new MapperOnlyCreationPreparation(
              selection(
                  TestIntegrationCatalogs.nativeIsActive(), NativeConnectionSchema.V1_REFERENCE),
              new ConnectionMappingRegistry(
                  List.of(NATIVE), List.of(new NativeConnectionMappingContribution(NATIVE))),
              new ConnectionMapperSelection(mappers, EgressMapperContributions.of(List.of())),
              new ConnectionExtractorRegistry(
                  List.of(NATIVE),
                  List.of(
                      new NativeKafkaProducerExtractor(NATIVE),
                      new NativePlainTcpExtractor(NATIVE)))),
          new ConnectionSettingsRegistry(List.of(NATIVE), List.of()),
          mock(ConnectionStore.class));

  /** The broker resources are provisioned as the connection is stored, from this material. */
  @Test
  void resolvesAThrottledProducerReadingWithTheSelectedMapper() {
    when(mappers.findLatest(MAPPER_ID)).thenReturn(Optional.of(ingressMapper()));

    var setup = resolution.resolve(creation("4096"));

    assertThat(setup.security()).isEqualTo(new ScramSecurityMaterial(4096L));
    assertThat(setup.access()).isEqualTo(new ReadAccess(MAPPER_ID));
    assertThat(setup.integration()).isEqualTo(NativeConnectionSchema.V1_REFERENCE);
    assertThat(setup.prefill().extractorId()).isEqualTo(NativeKafkaProducerExtractor.EXTRACTOR_ID);
    assertThat(setup.prefill().values())
        .containsExactly(Map.entry(KafkaProducerByteRatePolicy.PRODUCER_BYTE_RATE_FIELD, "4096"));
  }

  /** A bad throttle is caught before the broker provisions anything there is then to undo. */
  @Test
  void rejectsAnUnusableThrottle() {
    when(mappers.findLatest(MAPPER_ID)).thenReturn(Optional.of(ingressMapper()));

    assertThatThrownBy(() -> resolution.resolve(creation("0")))
        .isInstanceOfSatisfying(
            GlobalOpenCopException.class,
            exception -> {
              assertThat(exception.getCode())
                  .isEqualTo(ConnectionExceptionCodes.CONNECTION_INPUT_INVALID.code());
              assertThat(exception.getDetailCode()).isEqualTo("INVALID_PRODUCER_BYTE_RATE");
              assertThat(exception.getField())
                  .isEqualTo(KafkaProducerByteRatePolicy.PRODUCER_BYTE_RATE_FIELD);
            });
  }

  @Test
  void rejectsAnUnselectableMapper() {
    when(mappers.findLatest(MAPPER_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> resolution.resolve(creation(null)))
        .isInstanceOfSatisfying(
            GlobalOpenCopException.class,
            exception ->
                assertThat(exception.getCode())
                    .isEqualTo(ConnectionExceptionCodes.MAPPER_NOT_FOUND.code()));
  }

  @Test
  void refusesToWriteBecauseAKafkaConnectionOnlyConsumes() {
    assertThatThrownBy(() -> resolution.resolve(creation(null, "W")))
        .isInstanceOfSatisfying(
            GlobalOpenCopException.class,
            exception ->
                assertThat(exception.getCode())
                    .isEqualTo(ConnectionExceptionCodes.UNSUPPORTED_ACCESS_MODE.code()));
  }

  private static ConnectionCreation.Kafka creation(String producerByteRate) {
    return creation(producerByteRate, "R");
  }

  private static ConnectionCreation.Kafka creation(String producerByteRate, String access) {
    var values =
        producerByteRate == null
            ? List.<ConnectionInputValue>of()
            : List.of(
                new ConnectionInputValue(
                    KafkaProducerByteRatePolicy.PRODUCER_BYTE_RATE_FIELD, producerByteRate));
    return new ConnectionCreation.Kafka(
        new ConnectionCreationDetails(
            "Alpha Kafka",
            access,
            NativeConnectionSchema.V1_REFERENCE,
            NativeKafkaProducerExtractor.EXTRACTOR_ID,
            new DeclaredConnectionInput(values, List.of()),
            MAPPER_ID,
            ACTOR,
            null));
  }

  private static MapperProfile ingressMapper() {
    return MapperProfile.builder()
        .withId(MAPPER_ID)
        .withName("GeoJSON")
        .withQualifier(MapperQualifier.INGRESS)
        .withArchived(false)
        .build();
  }
}
