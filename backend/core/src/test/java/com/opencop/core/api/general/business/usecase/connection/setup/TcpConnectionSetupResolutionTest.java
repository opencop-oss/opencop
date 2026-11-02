// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.setup;

import static com.opencop.core.api.support.integration.TestIntegrationCatalogs.selection;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.opencop.core.api.component.support.TestActors;
import com.opencop.core.api.general.business.model.Caller;
import com.opencop.core.api.general.business.model.connection.access.ReadAccess;
import com.opencop.core.api.general.business.model.connection.access.ReadWriteAccess;
import com.opencop.core.api.general.business.model.connection.access.WriteAccess;
import com.opencop.core.api.general.business.model.connection.create.ConnectionCreation;
import com.opencop.core.api.general.business.model.connection.create.ConnectionCreationDetails;
import com.opencop.core.api.general.business.model.connection.create.TcpFramingSelection;
import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationConnectionSchemaKey;
import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationIdentity;
import com.opencop.core.api.general.business.usecase.connection.create.MapperOnlyCreationPreparation;
import com.opencop.core.api.general.business.usecase.connection.create.tcp.TcpConnectionSetupResolver;
import com.opencop.core.api.general.business.usecase.connection.mapping.ConnectionMapperSelection;
import com.opencop.core.api.general.ports.gateway.integrationcatalog.IntegrationCatalogGateway;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionExceptionCodes;
import com.opencop.core.api.general.technical.gateway.connection.certificate.KeyStoreCertificateStoreGateway;
import com.opencop.core.sdk.business.model.integration.connection.input.ConnectionInputResource;
import com.opencop.core.sdk.business.model.integration.connection.input.ConnectionInputValue;
import com.opencop.core.sdk.business.model.integration.connection.input.DeclaredConnectionInput;
import com.opencop.core.sdk.business.model.integration.mapping.egress.EgressMapperContributions;
import com.opencop.core.sdk.business.model.integration.mapping.egress.IntegrationEgressMappers;
import com.opencop.core.sdk.business.model.profile.DelimiterModes;
import com.opencop.core.sdk.business.model.profile.MapperProfile;
import com.opencop.core.sdk.business.model.profile.MapperQualifier;
import com.opencop.core.sdk.business.registry.integration.connection.input.ConnectionExtractorRegistry;
import com.opencop.core.sdk.business.registry.integration.connection.mapping.ConnectionMappingRegistry;
import com.opencop.core.sdk.business.registry.integration.connection.settings.ConnectionSettingsRegistry;
import com.opencop.core.sdk.integration.builtin.business.model.NativeConnectionMappingContribution;
import com.opencop.core.sdk.integration.builtin.business.model.NativeConnectionSchema;
import com.opencop.core.sdk.integration.builtin.business.model.NativeIntegration;
import com.opencop.core.sdk.integration.builtin.technical.extractor.tcp.NativeMtlsTcpExtractor;
import com.opencop.core.sdk.integration.builtin.technical.extractor.tcp.NativePlainTcpExtractor;
import com.opencop.core.sdk.model.connection.spec.security.MtlsConfig;
import com.opencop.core.sdk.model.connection.spec.transport.CustomTcpFraming;
import com.opencop.core.sdk.model.connection.spec.transport.TcpConfig;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.ports.gateway.mapper.MapperProfileService;
import com.opencop.core.sdk.ports.gateway.mapping.egress.PayloadEgressMapper;
import com.opencop.core.sdk.ports.gateway.secrets.ConnectionStore;
import com.opencop.core.sdk.testing.connection.TestCertificateStores;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TcpConnectionSetupResolutionTest {

  private static final UUID USER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000c1");
  private static final Caller ACTOR = TestActors.of(USER_ID);
  private static final UUID SOURCE_ID = UUID.fromString("00000000-0000-4000-8000-0000000000d1");
  private static final UUID MAPPER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
  private static final UUID FRAMING_ID = UUID.fromString("00000000-0000-4000-8000-0000000000b1");
  private static final UUID EGRESS_MAPPER_ID =
      UUID.fromString("00000000-0000-4000-8000-0000000000e2");
  private static final String HOST = "feed.example.test";
  private static final String CLIENT_PASSWORD = "client-secret";
  private static final String CLIENT_STORE_REF = "opencop/shared/connect/source/client";
  private static final TcpFramingSelection.Custom LINES =
      new TcpFramingSelection.Custom("delimiterSplit", "\n");
  private static final NativeConnectionSchema NATIVE =
      new NativeConnectionSchema(new NativeIntegration());

  private final MapperProfileService mappers = mock(MapperProfileService.class);
  private final ConnectionStore connectionStore = mock(ConnectionStore.class);
  private final IntegrationCatalogGateway catalog = mock(IntegrationCatalogGateway.class);
  private final PayloadEgressMapper egressMapper = egressMapper();
  private final ConnectionSetupResolution resolution =
      new ConnectionSetupResolution(
          new TcpConnectionSetupResolver(
              selection(catalog, NativeConnectionSchema.V1_REFERENCE),
              new ConnectionMappingRegistry(
                  List.of(NATIVE), List.of(new NativeConnectionMappingContribution(NATIVE))),
              new ConnectionExtractorRegistry(
                  List.of(NATIVE),
                  List.of(new NativePlainTcpExtractor(NATIVE), new NativeMtlsTcpExtractor(NATIVE))),
              new ConnectionMapperSelection(
                  mappers,
                  EgressMapperContributions.of(
                      List.of(
                          new IntegrationEgressMappers(
                              new NativeIntegration(), List.of(egressMapper))))),
              new KeyStoreCertificateStoreGateway()),
          mock(MapperOnlyCreationPreparation.class),
          new ConnectionSettingsRegistry(List.of(NATIVE), List.of()),
          connectionStore);

  @BeforeEach
  void nativeIsSelectable() {
    when(catalog.findSchemaOwner(
            new IntegrationConnectionSchemaKey(NativeIntegration.NATIVE_ID, 1)))
        .thenReturn(
            Optional.of(
                new IntegrationIdentity(NativeIntegration.NATIVE_ID, "Native", "Native", true)));
  }

  @Test
  void resolvesAPlainConnectionWithItsCustomFramingExactlyAsSubmitted() {
    selectableMapper();

    var setup = resolution.resolve(plain(LINES), SOURCE_ID);

    assertThat(setup.integration()).isEqualTo(NativeConnectionSchema.V1_REFERENCE);
    assertThat(setup.access()).isEqualTo(new ReadAccess(MAPPER_ID));
    var transport = (TcpConfig) setup.spec().transport();
    assertThat(transport.host()).isEqualTo(HOST);
    assertThat(transport.port()).isEqualTo(8089);
    assertThat(transport.framing())
        .isEqualTo(new CustomTcpFraming(DelimiterModes.DELIMITER_SPLIT, "\n"));
    assertThat(setup.settings().isEmpty()).isTrue();
    assertThat(setup.cleanup()).isEmpty();
    verifyNoInteractions(connectionStore);
  }

  /** The setup form is filled from the text the setup submitted, never from a secret. */
  @Test
  void capturesTheExtractorAndItsTextValuesButNeverTheClientPassword() {
    selectableMapper();
    when(connectionStore.saveClientCerts(any(), any(), any(), eq(CLIENT_PASSWORD)))
        .thenReturn(CLIENT_STORE_REF);

    var prefill =
        resolution
            .resolve(
                mtls(CLIENT_PASSWORD, TestCertificateStores.pkcs12(CLIENT_PASSWORD)), SOURCE_ID)
            .prefill();

    assertThat(prefill.extractorId()).isEqualTo(NativeMtlsTcpExtractor.EXTRACTOR_ID);
    assertThat(prefill.values()).containsOnlyKeys("host", "port");
    assertThat(prefill.values()).doesNotContainValue(CLIENT_PASSWORD);
  }

  /** A writing connection holds the egress mapper alone; one that also reads holds both. */
  @Test
  void resolvesTheMappersEachAccessModeUses() {
    selectableMapper();
    selectableEgressMapper();

    var writing = resolution.resolve(plain(LINES, "W", null, EGRESS_MAPPER_ID), SOURCE_ID);
    var both = resolution.resolve(plain(LINES, "RW", MAPPER_ID, EGRESS_MAPPER_ID), SOURCE_ID);

    assertThat(writing.access()).isEqualTo(new WriteAccess(EGRESS_MAPPER_ID));
    assertThat(both.access()).isEqualTo(new ReadWriteAccess(MAPPER_ID, EGRESS_MAPPER_ID));
  }

  /** A valid certificate store must resolve; direct mTLS once rejected them all. */
  @Test
  void storesAValidClientCertificateForTheConnectionAndReferencesItFromTheSpec() {
    selectableMapper();
    when(connectionStore.saveClientCerts(any(), any(), any(), eq(CLIENT_PASSWORD)))
        .thenReturn(CLIENT_STORE_REF);

    var setup =
        resolution.resolve(
            mtls(CLIENT_PASSWORD, TestCertificateStores.pkcs12(CLIENT_PASSWORD)), SOURCE_ID);

    verify(connectionStore).saveClientCerts(eq(SOURCE_ID), any(), any(), eq(CLIENT_PASSWORD));
    var security = (MtlsConfig) ((TcpConfig) setup.spec().transport()).security();
    assertThat(security.keyStore().uri()).isEqualTo(CLIENT_STORE_REF);
  }

  /**
   * Storing the setup can fail after the certificates are stored, and must not leave them behind.
   */
  @Test
  void itsCleanupDeletesExactlyTheStoredCertificates() {
    selectableMapper();
    when(connectionStore.saveClientCerts(any(), any(), any(), eq(CLIENT_PASSWORD)))
        .thenReturn(CLIENT_STORE_REF);

    var setup =
        resolution.resolve(
            mtls(CLIENT_PASSWORD, TestCertificateStores.pkcs12(CLIENT_PASSWORD)), SOURCE_ID);
    setup.cleanup().orElseThrow().run();

    verify(connectionStore).delete(List.of(CLIENT_STORE_REF));
  }

  @Test
  void rejectsAStoreThatDoesNotOpenWithItsPasswordBeforeStoringAnything() {
    selectableMapper();

    assertRejected(
        () ->
            resolution.resolve(
                mtls("wrong-password", TestCertificateStores.pkcs12(CLIENT_PASSWORD)), SOURCE_ID),
        ConnectionExceptionCodes.CONNECTION_INPUT_INVALID,
        "keyStore");
    verifyNoInteractions(connectionStore);
  }

  /** Native provides no framing in code, so a connection can only frame by its own delimiter. */
  @Test
  void rejectsAnIntegrationFramingOnNativeBeforeAnySideEffect() {
    assertRejected(
        () -> resolution.resolve(plain(new TcpFramingSelection.Integration(FRAMING_ID)), SOURCE_ID),
        ConnectionExceptionCodes.FRAMING_NOT_FOUND,
        TcpFramingSelection.Integration.FRAMING_ID_FIELD);
    verifyNoInteractions(mappers, connectionStore);
  }

  @Test
  void requiresAFraming() {
    assertRejected(
        () -> resolution.resolve(plain(null), SOURCE_ID),
        ConnectionExceptionCodes.REQUIRED_FIELD_INVALID,
        TcpFramingSelection.FIELD);
    assertRejected(
        () -> resolution.resolve(plain(new TcpFramingSelection.Integration(null)), SOURCE_ID),
        ConnectionExceptionCodes.REQUIRED_FIELD_INVALID,
        TcpFramingSelection.Integration.FRAMING_ID_FIELD);
    verifyNoInteractions(mappers, connectionStore);
  }

  @Test
  void rejectsAnUnusableCustomFramingBeforeAnySideEffect() {
    assertRejected(
        () ->
            resolution.resolve(
                plain(new TcpFramingSelection.Custom("passthrough", "\n")), SOURCE_ID),
        ConnectionExceptionCodes.INVALID_CUSTOM_FRAMING,
        TcpFramingSelection.Custom.DELIMITER_FIELD);
    verifyNoInteractions(mappers, connectionStore);
  }

  @Test
  void rejectsAnUnselectableMapper() {
    when(mappers.findLatest(MAPPER_ID)).thenReturn(Optional.empty());

    assertRejected(
        () -> resolution.resolve(plain(LINES), SOURCE_ID),
        ConnectionExceptionCodes.MAPPER_NOT_FOUND,
        ConnectionCreationDetails.INGRESS_MAPPER_FIELD);
    verifyNoInteractions(connectionStore);
  }

  @Test
  void rejectsAnExtractorNativeDoesNotRegisterForTcp() {
    selectableMapper();

    assertRejected(
        () ->
            resolution.resolve(creation(UUID.randomUUID(), List.of(), List.of(), LINES), SOURCE_ID),
        ConnectionExceptionCodes.EXTRACTOR_NOT_FOUND,
        ConnectionCreationDetails.EXTRACTOR_FIELD);
  }

  private static PayloadEgressMapper egressMapper() {
    var mapper = mock(PayloadEgressMapper.class);
    when(mapper.id()).thenReturn(EGRESS_MAPPER_ID);
    return mapper;
  }

  private void selectableEgressMapper() {
    when(mappers.findLatest(EGRESS_MAPPER_ID))
        .thenReturn(
            Optional.of(
                MapperProfile.builder()
                    .withId(EGRESS_MAPPER_ID)
                    .withVersion(1)
                    .withQualifier(MapperQualifier.EGRESS)
                    .withArchived(false)
                    .build()));
  }

  private void selectableMapper() {
    when(mappers.findLatest(MAPPER_ID))
        .thenReturn(
            Optional.of(
                MapperProfile.builder()
                    .withId(MAPPER_ID)
                    .withVersion(1)
                    .withQualifier(MapperQualifier.INGRESS)
                    .withArchived(false)
                    .build()));
  }

  private static void assertRejected(
      ThrowingCallable creation, ConnectionExceptionCodes code, String field) {
    assertThatThrownBy(creation)
        .isInstanceOfSatisfying(
            GlobalOpenCopException.class,
            exception -> {
              assertThat(exception.getCode()).isEqualTo(code.code());
              assertThat(exception.getField()).isEqualTo(field);
            });
  }

  private static ConnectionCreation.Tcp plain(TcpFramingSelection framing) {
    return creation(NativePlainTcpExtractor.EXTRACTOR_ID, List.of(), List.of(), framing);
  }

  private static ConnectionCreation.Tcp plain(
      TcpFramingSelection framing, String access, UUID ingressMapperId, UUID egressMapperId) {
    return new ConnectionCreation.Tcp(
        new ConnectionCreationDetails(
            "Feed",
            access,
            NativeConnectionSchema.V1_REFERENCE,
            NativePlainTcpExtractor.EXTRACTOR_ID,
            new DeclaredConnectionInput(
                List.of(
                    new ConnectionInputValue("host", HOST),
                    new ConnectionInputValue("port", "8089")),
                List.of()),
            ingressMapperId,
            ACTOR,
            null),
        framing,
        egressMapperId);
  }

  private static ConnectionCreation.Tcp mtls(String clientPassword, byte[] clientStore) {
    return creation(
        NativeMtlsTcpExtractor.EXTRACTOR_ID,
        List.of(new ConnectionInputValue("clientPassword", clientPassword)),
        List.of(
            new ConnectionInputResource(
                "keyStore",
                "client.p12",
                "application/x-pkcs12",
                clientStore.length,
                () -> new ByteArrayInputStream(clientStore))),
        LINES);
  }

  private static ConnectionCreation.Tcp creation(
      UUID extractorId,
      List<ConnectionInputValue> extraValues,
      List<ConnectionInputResource> files,
      TcpFramingSelection framing) {
    var values = new ArrayList<ConnectionInputValue>();
    values.add(new ConnectionInputValue("host", HOST));
    values.add(new ConnectionInputValue("port", "8089"));
    values.addAll(extraValues);
    return new ConnectionCreation.Tcp(
        new ConnectionCreationDetails(
            "Feed",
            "R",
            NativeConnectionSchema.V1_REFERENCE,
            extractorId,
            new DeclaredConnectionInput(values, files),
            MAPPER_ID,
            ACTOR,
            null),
        framing,
        null);
  }
}
