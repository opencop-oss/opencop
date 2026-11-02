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
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchema;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.business.model.integration.TransportProtocol;
import com.opencop.core.sdk.business.model.integration.connection.input.ConnectionInput;
import com.opencop.core.sdk.business.model.integration.connection.input.ConnectionInputValue;
import com.opencop.core.sdk.business.model.integration.connection.input.DeclaredConnectionInput;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.ExtractorDescriptor;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.StandardExtractorField;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.TextField;
import com.opencop.core.sdk.business.model.integration.connection.mapping.FramingDefinition;
import com.opencop.core.sdk.business.model.integration.connection.mapping.MapperDeclaration;
import com.opencop.core.sdk.business.model.integration.connection.material.udp.UdpConnectionMaterial;
import com.opencop.core.sdk.business.model.integration.mapping.egress.EgressMapperContributions;
import com.opencop.core.sdk.business.model.profile.MapperProfile;
import com.opencop.core.sdk.business.model.profile.MapperQualifier;
import com.opencop.core.sdk.business.registry.integration.connection.input.ConnectionExtractorRegistry;
import com.opencop.core.sdk.business.registry.integration.connection.mapping.ConnectionMappingRegistry;
import com.opencop.core.sdk.business.registry.integration.connection.settings.ConnectionSettingsRegistry;
import com.opencop.core.sdk.integration.builtin.business.model.NativeConnectionMappingContribution;
import com.opencop.core.sdk.integration.builtin.business.model.NativeConnectionSchema;
import com.opencop.core.sdk.integration.builtin.business.model.NativeIntegration;
import com.opencop.core.sdk.integration.builtin.business.policy.udp.UdpListenPortPolicy;
import com.opencop.core.sdk.integration.builtin.technical.extractor.tcp.NativePlainTcpExtractor;
import com.opencop.core.sdk.integration.builtin.technical.extractor.udp.NativeUdpListenerExtractor;
import com.opencop.core.sdk.model.connection.spec.transport.UdpConfig;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.ports.gateway.mapper.MapperProfileService;
import com.opencop.core.sdk.ports.gateway.secrets.ConnectionStore;
import com.opencop.core.sdk.ports.integration.connection.input.ConnectionExtractorCapability;
import com.opencop.core.sdk.ports.integration.connection.input.UdpConnectionExtractorCapability;
import com.opencop.core.sdk.ports.integration.connection.mapping.ConnectionMappingContribution;
import com.opencop.core.sdk.testing.integration.connection.behavior.TestConnectionSchema;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UdpConnectionSetupResolutionTest {

  private static final UUID USER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000c1");
  private static final Caller ACTOR = TestActors.of(USER_ID);
  private static final UUID SOURCE_ID = UUID.fromString("00000000-0000-4000-8000-0000000000d1");
  private static final UUID MAPPER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
  private static final NativeConnectionSchema NATIVE =
      new NativeConnectionSchema(new NativeIntegration());
  private static final IntegrationConnectionSchemaReference WITHOUT_UDP_MAPPING =
      new IntegrationConnectionSchemaReference(
          UUID.fromString("3c9e2a71-5b04-4d8e-a6f1-0e7b2c9d4a53"), 1);

  private final MapperProfileService mappers = mock(MapperProfileService.class);

  private final ConnectionSetupResolution resolution =
      new ConnectionSetupResolution(
          mock(TcpConnectionSetupResolver.class),
          new MapperOnlyCreationPreparation(
              selection(
                  TestIntegrationCatalogs.nativeIsActive(),
                  NativeConnectionSchema.V1_REFERENCE,
                  WITHOUT_UDP_MAPPING),
              new ConnectionMappingRegistry(
                  List.of(NATIVE), List.of(new NativeConnectionMappingContribution(NATIVE))),
              new ConnectionMapperSelection(mappers, EgressMapperContributions.of(List.of())),
              new ConnectionExtractorRegistry(
                  List.of(NATIVE),
                  List.of(
                      new NativeUdpListenerExtractor(NATIVE),
                      new NativePlainTcpExtractor(NATIVE)))),
          new ConnectionSettingsRegistry(List.of(NATIVE), List.of()),
          mock(ConnectionStore.class));

  @Test
  void resolvesANativeListenerReadingWithTheSelectedMapper() {
    when(mappers.findLatest(MAPPER_ID)).thenReturn(Optional.of(ingressMapper(false)));

    var setup = resolution.resolve(creation(input("0.0.0.0", "8087")), SOURCE_ID);

    assertThat(setup.integration()).isEqualTo(NativeConnectionSchema.V1_REFERENCE);
    assertThat(setup.access()).isEqualTo(new ReadAccess(MAPPER_ID));
    assertThat(setup.spec().transport()).isEqualTo(new UdpConfig("0.0.0.0", 8087, null));
    assertThat(setup.prefill().extractorId()).isEqualTo(NativeUdpListenerExtractor.EXTRACTOR_ID);
    assertThat(setup.prefill().values())
        .containsOnly(Map.entry("host", "0.0.0.0"), Map.entry("listenPort", "8087"));
  }

  @Test
  void refusesToWriteBecauseAUdpConnectionOnlyListens() {
    assertRejected(
        () -> resolution.resolve(creation(input("0.0.0.0", "8087"), "RW"), SOURCE_ID),
        ConnectionExceptionCodes.UNSUPPORTED_ACCESS_MODE.code());
    assertRejected(
        () -> resolution.resolve(creation(input("0.0.0.0", "8087"), "W"), SOURCE_ID),
        ConnectionExceptionCodes.UNSUPPORTED_ACCESS_MODE.code());
  }

  @Test
  void requiresAMapperToReadWith() {
    assertRejected(
        () -> resolution.resolve(creationWithoutMapper(), SOURCE_ID),
        ConnectionExceptionCodes.INGRESS_MAPPER_REQUIRED.code());
  }

  @Test
  void rejectsAMapperTheCatalogDoesNotHoldAsASelectableIngressMapper() {
    when(mappers.findLatest(MAPPER_ID)).thenReturn(Optional.empty());
    assertRejected(
        () -> resolution.resolve(creation(input("0.0.0.0", "8087")), SOURCE_ID),
        ConnectionExceptionCodes.MAPPER_NOT_FOUND.code());

    when(mappers.findLatest(MAPPER_ID)).thenReturn(Optional.of(egressMapper()));
    assertRejected(
        () -> resolution.resolve(creation(input("0.0.0.0", "8087")), SOURCE_ID),
        ConnectionExceptionCodes.MAPPER_NOT_FOUND.code());

    when(mappers.findLatest(MAPPER_ID)).thenReturn(Optional.of(ingressMapper(true)));
    assertRejected(
        () -> resolution.resolve(creation(input("0.0.0.0", "8087")), SOURCE_ID),
        ConnectionExceptionCodes.MAPPER_ARCHIVED.code());
  }

  @Test
  void rejectsAMapperTheIntegrationDoesNotPermit() {
    var closed = schema("6a0f2b8e-9c1d-4e3f-8a7b-5c6d7e8f9a0b");
    var resolutionForClosed =
        resolutionFor(closed, MapperDeclaration.closedTo(Set.of(UUID.randomUUID())), List.of());
    when(mappers.findLatest(MAPPER_ID)).thenReturn(Optional.of(ingressMapper(false)));

    assertRejected(
        () ->
            resolutionForClosed.resolve(
                creation(closed.reference(), NativeUdpListenerExtractor.EXTRACTOR_ID), SOURCE_ID),
        ConnectionExceptionCodes.MAPPER_NOT_ALLOWED.code());
  }

  @Test
  void rejectsAnIntegrationThatDeclaresNoUdpMapping() {
    assertRejected(
        () ->
            resolution.resolve(
                creation(WITHOUT_UDP_MAPPING, NativeUdpListenerExtractor.EXTRACTOR_ID), SOURCE_ID),
        ConnectionExceptionCodes.INTEGRATION_DECLARATION_NOT_FOUND.code());
  }

  @Test
  void rejectsAnIntegrationThisRuntimeRegistersNoImplementationFor() {
    var unregistered = new IntegrationConnectionSchemaReference(UUID.randomUUID(), 1);

    assertRejected(
        () ->
            resolution.resolve(
                creation(unregistered, NativeUdpListenerExtractor.EXTRACTOR_ID), SOURCE_ID),
        ConnectionExceptionCodes.INTEGRATION_UNAVAILABLE.code());
  }

  @Test
  void rejectsAnExtractorTheIntegrationDoesNotRegisterForUdp() {
    when(mappers.findLatest(MAPPER_ID)).thenReturn(Optional.of(ingressMapper(false)));

    assertThatThrownBy(
            () ->
                resolution.resolve(
                    creation(NativeConnectionSchema.V1_REFERENCE, UUID.randomUUID()), SOURCE_ID))
        .isInstanceOfSatisfying(
            GlobalOpenCopException.class,
            exception -> {
              assertThat(exception.getCode())
                  .isEqualTo(ConnectionExceptionCodes.EXTRACTOR_NOT_FOUND.code());
              assertThat(exception.getField()).isEqualTo("extractorId");
            });
  }

  /** The requester fixes their own input, so they learn what was wrong and with which field. */
  @Test
  void answersAnInputRejectionWithTheExtractorsReasonAndField() {
    when(mappers.findLatest(MAPPER_ID)).thenReturn(Optional.of(ingressMapper(false)));

    assertThatThrownBy(() -> resolution.resolve(creation(input("0.0.0.0", "70000")), SOURCE_ID))
        .isInstanceOfSatisfying(
            GlobalOpenCopException.class,
            exception -> {
              assertThat(exception.getCode())
                  .isEqualTo(ConnectionExceptionCodes.CONNECTION_INPUT_INVALID.code());
              assertThat(exception.getMessage())
                  .isEqualTo("Listen port must be between 1 and 65535");
              assertThat(exception.getDetailCode()).isEqualTo(UdpListenPortPolicy.INVALID_PORT);
              assertThat(exception.getField()).isEqualTo("listenPort");
            });
  }

  /** A failing plugin is not the requester's to fix, so nothing about it reaches them. */
  @Test
  void answersAnExtractorFailureAsAnInternalError() {
    var plugin = schema("7b1e3c9f-0d2e-4f40-9b8c-6d7e8f9a0b1c");
    var failing = new FailingExtractor(plugin);
    var resolutionWithFailing =
        resolutionFor(plugin, MapperDeclaration.openMapper(), List.of(failing));
    when(mappers.findLatest(MAPPER_ID)).thenReturn(Optional.of(ingressMapper(false)));

    assertThatThrownBy(
            () ->
                resolutionWithFailing.resolve(
                    creation(plugin.reference(), FailingExtractor.ID), SOURCE_ID))
        .isInstanceOfSatisfying(
            GlobalOpenCopException.class,
            exception -> {
              assertThat(exception.getCode()).isEqualTo("INTERNAL_ERROR");
              assertThat(exception.getMessage()).doesNotContain("plugin bug");
            });
  }

  private ConnectionSetupResolution resolutionFor(
      IntegrationConnectionSchema schema,
      MapperDeclaration declaration,
      List<ConnectionExtractorCapability<?, ?>> pluginExtractors) {
    var extractors = new ArrayList<ConnectionExtractorCapability<?, ?>>(pluginExtractors);
    extractors.add(new NativeUdpListenerExtractor(NATIVE));
    extractors.add(new NativePlainTcpExtractor(NATIVE));
    return new ConnectionSetupResolution(
        mock(TcpConnectionSetupResolver.class),
        new MapperOnlyCreationPreparation(
            selection(
                TestIntegrationCatalogs.nativeIsActive(),
                NativeConnectionSchema.V1_REFERENCE,
                schema.reference()),
            new ConnectionMappingRegistry(
                List.of(NATIVE, schema),
                List.of(
                    new NativeConnectionMappingContribution(NATIVE),
                    contribution(schema, declaration))),
            new ConnectionMapperSelection(mappers, EgressMapperContributions.of(List.of())),
            new ConnectionExtractorRegistry(List.of(NATIVE, schema), extractors)),
        new ConnectionSettingsRegistry(List.of(NATIVE, schema), List.of()),
        mock(ConnectionStore.class));
  }

  private static void assertRejected(Runnable creation, String code) {
    assertThatThrownBy(creation::run)
        .isInstanceOfSatisfying(
            GlobalOpenCopException.class,
            exception -> assertThat(exception.getCode()).isEqualTo(code));
  }

  private static ConnectionCreation.Udp creation(ConnectionInput input) {
    return creation(input, "R");
  }

  private static ConnectionCreation.Udp creation(ConnectionInput input, String access) {
    return new ConnectionCreation.Udp(
        new ConnectionCreationDetails(
            "Alpha UDP",
            access,
            NativeConnectionSchema.V1_REFERENCE,
            NativeUdpListenerExtractor.EXTRACTOR_ID,
            input,
            MAPPER_ID,
            ACTOR,
            null));
  }

  private static ConnectionCreation.Udp creation(
      IntegrationConnectionSchemaReference integration, UUID extractorId) {
    return new ConnectionCreation.Udp(
        new ConnectionCreationDetails(
            "Alpha UDP",
            "R",
            integration,
            extractorId,
            input("0.0.0.0", "8087"),
            MAPPER_ID,
            ACTOR,
            null));
  }

  private static ConnectionCreation.Udp creationWithoutMapper() {
    return new ConnectionCreation.Udp(
        new ConnectionCreationDetails(
            "Alpha UDP",
            "R",
            NativeConnectionSchema.V1_REFERENCE,
            NativeUdpListenerExtractor.EXTRACTOR_ID,
            input("0.0.0.0", "8087"),
            null,
            ACTOR,
            null));
  }

  private static DeclaredConnectionInput input(String host, String listenPort) {
    return new DeclaredConnectionInput(
        List.of(
            new ConnectionInputValue("host", host),
            new ConnectionInputValue("listenPort", listenPort)),
        List.of());
  }

  private static TestConnectionSchema schema(String id) {
    return new TestConnectionSchema(
        UUID.fromString(id), 1, "UDP plugin", "UDP plugin", Set.of(TransportProtocol.UDP));
  }

  private static ConnectionMappingContribution contribution(
      IntegrationConnectionSchema schema, MapperDeclaration declaration) {
    return new ConnectionMappingContribution() {
      public IntegrationConnectionSchema schema() {
        return schema;
      }

      public Map<TransportProtocol, MapperDeclaration> declarations() {
        return Map.of(TransportProtocol.UDP, declaration);
      }

      public Set<FramingDefinition> framings() {
        return Set.of();
      }
    };
  }

  private static MapperProfile ingressMapper(boolean archived) {
    return MapperProfile.builder()
        .withId(MAPPER_ID)
        .withName("GeoJSON")
        .withQualifier(MapperQualifier.INGRESS)
        .withArchived(archived)
        .build();
  }

  private static MapperProfile egressMapper() {
    return MapperProfile.builder()
        .withId(MAPPER_ID)
        .withName("CoT out")
        .withQualifier(MapperQualifier.EGRESS)
        .withArchived(false)
        .build();
  }

  private record FailingExtractor(IntegrationConnectionSchema schema)
      implements UdpConnectionExtractorCapability {

    static final UUID ID = UUID.fromString("8c2f4d0a-1e3f-4051-8c9d-7e8f9a0b1c2d");

    @Override
    public UUID id() {
      return ID;
    }

    @Override
    public ExtractorDescriptor descriptor() {
      return new ExtractorDescriptor(
          ID,
          "Failing",
          List.of(
              StandardExtractorField.HOST.declare(true),
              new TextField("listenPort", "Listen port", true)));
    }

    @Override
    public void validate(ConnectionInput input) {
      throw new IllegalStateException("plugin bug");
    }

    @Override
    public UdpConnectionMaterial extract(ConnectionInput input) {
      throw new IllegalStateException("plugin bug");
    }
  }
}
