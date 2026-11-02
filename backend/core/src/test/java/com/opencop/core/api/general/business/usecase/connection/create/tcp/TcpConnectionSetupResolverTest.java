// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.create.tcp;

import static com.opencop.core.api.support.integration.TestIntegrationCatalogs.selection;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.opencop.core.api.component.support.TestActors;
import com.opencop.core.api.general.business.model.Caller;
import com.opencop.core.api.general.business.model.connection.access.ReadWriteAccess;
import com.opencop.core.api.general.business.model.connection.create.ConnectionCreation;
import com.opencop.core.api.general.business.model.connection.create.ConnectionCreationDetails;
import com.opencop.core.api.general.business.model.connection.create.TcpFramingSelection;
import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationIdentity;
import com.opencop.core.api.general.business.usecase.connection.mapping.ConnectionMapperSelection;
import com.opencop.core.api.general.ports.gateway.connection.certificate.CertificateStoreGateway;
import com.opencop.core.api.general.ports.gateway.integrationcatalog.IntegrationCatalogGateway;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionExceptionCodes;
import com.opencop.core.api.support.integration.StubConnectionMappingContribution;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchema;
import com.opencop.core.sdk.business.model.integration.TransportProtocol;
import com.opencop.core.sdk.business.model.integration.connection.input.DeclaredConnectionInput;
import com.opencop.core.sdk.business.model.integration.connection.input.TcpConnectionInput;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.ExtractorDescriptor;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.StandardExtractorField;
import com.opencop.core.sdk.business.model.integration.connection.mapping.EgressMapperRule;
import com.opencop.core.sdk.business.model.integration.connection.mapping.MapperFramingDeclaration;
import com.opencop.core.sdk.business.model.integration.connection.mapping.MapperFramingPair;
import com.opencop.core.sdk.business.model.integration.connection.material.certificate.CertificateMaterial;
import com.opencop.core.sdk.business.model.integration.connection.material.certificate.CertificateStoreFormat;
import com.opencop.core.sdk.business.model.integration.connection.material.certificate.DetachedBinaryMaterial;
import com.opencop.core.sdk.business.model.integration.connection.material.tcp.MtlsCredentialMaterial;
import com.opencop.core.sdk.business.model.integration.connection.material.tcp.TcpConnectionMaterial;
import com.opencop.core.sdk.business.model.integration.connection.material.tcp.TcpEndpointMaterial;
import com.opencop.core.sdk.business.model.integration.connection.setup.ResolvedTcpFraming;
import com.opencop.core.sdk.business.model.integration.mapping.egress.EgressMapperContributions;
import com.opencop.core.sdk.business.model.integration.mapping.egress.IntegrationEgressMappers;
import com.opencop.core.sdk.business.model.profile.MapperProfile;
import com.opencop.core.sdk.business.model.profile.MapperQualifier;
import com.opencop.core.sdk.business.model.profile.MapperReference;
import com.opencop.core.sdk.business.registry.integration.connection.input.ConnectionExtractorRegistry;
import com.opencop.core.sdk.business.registry.integration.connection.mapping.ConnectionMappingRegistry;
import com.opencop.core.sdk.model.datasource.AccessMode;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.ports.gateway.mapper.MapperProfileService;
import com.opencop.core.sdk.ports.gateway.mapping.egress.PayloadEgressMapper;
import com.opencop.core.sdk.ports.integration.connection.input.ConnectionInputException;
import com.opencop.core.sdk.ports.integration.connection.input.ConnectionInputFailure;
import com.opencop.core.sdk.ports.integration.connection.input.TcpConnectionExtractorCapability;
import com.opencop.core.sdk.testing.integration.connection.behavior.TestConnectionSchema;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TcpConnectionSetupResolverTest {

  private static final UUID USER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000c1");
  private static final Caller ACTOR = TestActors.of(USER_ID);
  private static final UUID FIRST_MAPPER = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
  private static final UUID SECOND_MAPPER = UUID.fromString("00000000-0000-4000-8000-0000000000a2");
  private static final UUID FIRST_FRAMING = UUID.fromString("00000000-0000-4000-8000-0000000000b1");
  private static final UUID SECOND_FRAMING =
      UUID.fromString("00000000-0000-4000-8000-0000000000b2");
  private static final UUID EGRESS = UUID.fromString("00000000-0000-4000-8000-0000000000e1");
  private static final UUID OTHER_EGRESS = UUID.fromString("00000000-0000-4000-8000-0000000000e2");
  private static final UUID ARCHIVED_EGRESS =
      UUID.fromString("00000000-0000-4000-8000-0000000000e3");
  private static final UUID FIRST_EXTRACTOR =
      UUID.fromString("00000000-0000-4000-8000-0000000000f1");
  private static final UUID SECOND_EXTRACTOR =
      UUID.fromString("00000000-0000-4000-8000-0000000000f2");
  private static final UUID REJECTING_EXTRACTOR =
      UUID.fromString("00000000-0000-4000-8000-0000000000f3");
  private static final UUID READ_ONLY_EXTRACTOR =
      UUID.fromString("00000000-0000-4000-8000-0000000000f4");
  private static final UUID MTLS_EXTRACTOR =
      UUID.fromString("00000000-0000-4000-8000-0000000000f5");
  private static final String CLIENT_PASSWORD = "client-secret";
  private static final String CA_PASSWORD = "ca-secret";

  private static final TestConnectionSchema FEED =
      new TestConnectionSchema(
          UUID.fromString("8a1f0c4e-2d3b-4a5c-9e6f-7b8c9d0e1f20"),
          1,
          "Feed",
          "Feed",
          Set.of(TransportProtocol.TCP));
  private static final TestConnectionSchema READ_ONLY =
      new TestConnectionSchema(
          UUID.fromString("9b2e1d5f-3e4c-4b6d-8f70-6c7d8e9f0a31"),
          1,
          "Read only",
          "Read only",
          Set.of(TransportProtocol.TCP));

  private static final TcpFramingSelection FIRST =
      new TcpFramingSelection.Integration(FIRST_FRAMING);
  private static final TcpFramingSelection SECOND =
      new TcpFramingSelection.Integration(SECOND_FRAMING);
  private static final UUID FOREIGN_FRAMING =
      UUID.fromString("00000000-0000-4000-8000-0000000000b3");
  private static final TcpFramingSelection FOREIGN =
      new TcpFramingSelection.Integration(FOREIGN_FRAMING);

  private final IntegrationCatalogGateway catalog = mock(IntegrationCatalogGateway.class);
  private final MapperProfileService mappers = mock(MapperProfileService.class);
  private final FixedExtractor first = new FixedExtractor(FEED, FIRST_EXTRACTOR, "first.test");
  private final FixedExtractor second = new FixedExtractor(FEED, SECOND_EXTRACTOR, "second.test");
  private final FixedExtractor rejecting = new FixedExtractor(FEED, REJECTING_EXTRACTOR, null);
  private final CertificateStoreGateway certificateStores = mock(CertificateStoreGateway.class);
  private final TcpConnectionSetupResolver resolver =
      new TcpConnectionSetupResolver(
          selection(catalog, FEED.reference(), READ_ONLY.reference()),
          new ConnectionMappingRegistry(
              List.of(FEED, READ_ONLY),
              List.of(
                  new StubConnectionMappingContribution(
                      FEED,
                      Map.of(
                          TransportProtocol.TCP,
                          MapperFramingDeclaration.of(
                                  new MapperFramingPair(FIRST_MAPPER, FIRST_FRAMING),
                                  new MapperFramingPair(SECOND_MAPPER, SECOND_FRAMING))
                              .withEgress(
                                  EgressMapperRule.closedTo(Set.of(EGRESS, ARCHIVED_EGRESS))))),
                  new StubConnectionMappingContribution(
                      READ_ONLY,
                      Map.of(
                          TransportProtocol.TCP,
                          MapperFramingDeclaration.of(
                              new MapperFramingPair(FIRST_MAPPER, FIRST_FRAMING),
                              new MapperFramingPair(FIRST_MAPPER, FOREIGN_FRAMING)))))),
          new ConnectionExtractorRegistry(
              List.of(FEED, READ_ONLY),
              List.of(
                  first,
                  second,
                  rejecting,
                  new MtlsExtractor(FEED, MTLS_EXTRACTOR),
                  new FixedExtractor(READ_ONLY, READ_ONLY_EXTRACTOR, "read-only.test"))),
          new ConnectionMapperSelection(
              mappers,
              EgressMapperContributions.of(
                  List.of(
                      new IntegrationEgressMappers(
                          FEED.integration(),
                          List.of(egressMapper(EGRESS), egressMapper(ARCHIVED_EGRESS)))))),
          certificateStores);

  @BeforeEach
  void stubCatalog() {
    when(catalog.findSchemaOwner(any()))
        .thenAnswer(
            invocation ->
                Optional.of(new IntegrationIdentity(FEED.integrationId(), "Feed", "Feed", true)));
    mapper(FIRST_MAPPER, MapperQualifier.INGRESS, false, 3);
    mapper(SECOND_MAPPER, MapperQualifier.INGRESS, false, 1);
    mapper(EGRESS, MapperQualifier.EGRESS, false, 2);
    mapper(OTHER_EGRESS, MapperQualifier.EGRESS, false, 1);
    mapper(ARCHIVED_EGRESS, MapperQualifier.EGRESS, true, 1);
    when(certificateStores.opensWithItsPassword(any())).thenReturn(true);
  }

  @Test
  void acceptsMtlsMaterialWhoseStoresOpenWithTheirPasswords() {
    var setup =
        resolver.resolve(read(FEED, MTLS_EXTRACTOR, FIRST_MAPPER, FIRST), AccessMode.READ).setup();

    assertThat(setup.material().security()).isInstanceOf(MtlsCredentialMaterial.class);
  }

  /**
   * Opening a store is OpenCOP's rule for every integration's extractor, so an extractor that
   * accepts a wrong password still has the connection refused before anything is stored.
   */
  @Test
  void rejectsAClientStoreThatDoesNotOpenWhicheverExtractorProducedIt() {
    when(certificateStores.opensWithItsPassword(withPassword(CLIENT_PASSWORD))).thenReturn(false);

    assertUnreadableStore(
        () -> resolver.resolve(read(FEED, MTLS_EXTRACTOR, FIRST_MAPPER, FIRST), AccessMode.READ),
        "The client certificate store does not open with its password");
  }

  @Test
  void rejectsATruststoreThatDoesNotOpen() {
    when(certificateStores.opensWithItsPassword(withPassword(CA_PASSWORD))).thenReturn(false);

    assertUnreadableStore(
        () -> resolver.resolve(read(FEED, MTLS_EXTRACTOR, FIRST_MAPPER, FIRST), AccessMode.READ),
        "The truststore does not open with its password");
  }

  @Test
  void opensNoStoreForAConnectionWithoutCertificates() {
    resolver.resolve(read(FEED, FIRST_EXTRACTOR, FIRST_MAPPER, FIRST), AccessMode.READ);

    verifyNoInteractions(certificateStores);
  }

  /** The request names its extractor, so none is ever picked by the shape of the input. */
  @Test
  void usesTheExtractorTheRequestNamesAmongSeveral() {
    var setup =
        resolver
            .resolve(read(FEED, SECOND_EXTRACTOR, FIRST_MAPPER, FIRST), AccessMode.READ)
            .setup();

    assertThat(setup.extractorId()).isEqualTo(SECOND_EXTRACTOR);
    assertThat(setup.material().endpoint().host()).isEqualTo("second.test");
    assertThat(first.extractions).hasValue(0);
  }

  @Test
  void resolvesEachDeclaredPairAndRecordsTheMapperRevisionAsProvenance() {
    var firstPair =
        resolver.resolve(read(FEED, FIRST_EXTRACTOR, FIRST_MAPPER, FIRST), AccessMode.READ).setup();
    var secondPair =
        resolver
            .resolve(read(FEED, FIRST_EXTRACTOR, SECOND_MAPPER, SECOND), AccessMode.READ)
            .setup();

    assertThat(firstPair.ingressMapper()).contains(new MapperReference(FIRST_MAPPER, 3));
    assertThat(firstPair.framing()).isEqualTo(new ResolvedTcpFraming.Integration(FIRST_FRAMING));
    assertThat(firstPair.egressMapper()).isEmpty();
    assertThat(secondPair.ingressMapper()).contains(new MapperReference(SECOND_MAPPER, 1));
  }

  @Test
  void rejectsAPairTheIntegrationDoesNotDeclareTogether() {
    assertRejected(
        () -> resolver.resolve(read(FEED, FIRST_EXTRACTOR, FIRST_MAPPER, SECOND), AccessMode.READ),
        ConnectionExceptionCodes.PAIR_NOT_ALLOWED,
        null);
    assertRejected(
        () ->
            resolver.resolve(
                read(
                    FEED,
                    FIRST_EXTRACTOR,
                    FIRST_MAPPER,
                    new TcpFramingSelection.Custom("delimiterSplit", "\n")),
                AccessMode.READ),
        ConnectionExceptionCodes.PAIR_NOT_ALLOWED,
        null);
    assertThat(first.extractions).hasValue(0);
  }

  /** Even an integration that permits a single pair is never given one silently. */
  @Test
  void requiresTheMapperAndFramingEvenWhenOnlyOnePairIsAllowed() {
    assertRejected(
        () -> resolver.resolve(read(READ_ONLY, READ_ONLY_EXTRACTOR, null, FIRST), AccessMode.READ),
        ConnectionExceptionCodes.INGRESS_MAPPER_REQUIRED,
        ConnectionCreationDetails.INGRESS_MAPPER_FIELD);
    assertRejected(
        () ->
            resolver.resolve(
                read(READ_ONLY, READ_ONLY_EXTRACTOR, FIRST_MAPPER, null), AccessMode.READ),
        ConnectionExceptionCodes.REQUIRED_FIELD_INVALID,
        TcpFramingSelection.FIELD);
  }

  @Test
  void rejectsAnExtractorTheIntegrationDoesNotRegister() {
    assertRejected(
        () -> resolver.resolve(read(FEED, UUID.randomUUID(), FIRST_MAPPER, FIRST), AccessMode.READ),
        ConnectionExceptionCodes.EXTRACTOR_NOT_FOUND,
        ConnectionCreationDetails.EXTRACTOR_FIELD);
    assertRejected(
        () ->
            resolver.resolve(read(FEED, READ_ONLY_EXTRACTOR, FIRST_MAPPER, FIRST), AccessMode.READ),
        ConnectionExceptionCodes.EXTRACTOR_NOT_FOUND,
        ConnectionCreationDetails.EXTRACTOR_FIELD);
  }

  @Test
  void rejectsAFramingNoIntegrationProvides() {
    assertRejected(
        () ->
            resolver.resolve(
                read(
                    FEED,
                    FIRST_EXTRACTOR,
                    FIRST_MAPPER,
                    new TcpFramingSelection.Integration(UUID.randomUUID())),
                AccessMode.READ),
        ConnectionExceptionCodes.FRAMING_NOT_FOUND,
        TcpFramingSelection.Integration.FRAMING_ID_FIELD);
  }

  @Test
  void rejectsAnIngressMapperTheCatalogCannotOffer() {
    mapper(FIRST_MAPPER, MapperQualifier.INGRESS, true, 3);
    assertRejected(
        () -> resolver.resolve(read(FEED, FIRST_EXTRACTOR, FIRST_MAPPER, FIRST), AccessMode.READ),
        ConnectionExceptionCodes.MAPPER_ARCHIVED,
        ConnectionCreationDetails.INGRESS_MAPPER_FIELD);
    mapper(FIRST_MAPPER, MapperQualifier.EGRESS, false, 3);
    assertRejected(
        () -> resolver.resolve(read(FEED, FIRST_EXTRACTOR, FIRST_MAPPER, FIRST), AccessMode.READ),
        ConnectionExceptionCodes.MAPPER_NOT_FOUND,
        ConnectionCreationDetails.INGRESS_MAPPER_FIELD);
    when(mappers.findLatest(FIRST_MAPPER)).thenReturn(Optional.empty());
    assertRejected(
        () -> resolver.resolve(read(FEED, FIRST_EXTRACTOR, FIRST_MAPPER, FIRST), AccessMode.READ),
        ConnectionExceptionCodes.MAPPER_NOT_FOUND,
        ConnectionCreationDetails.INGRESS_MAPPER_FIELD);
  }

  @Test
  void answersInvalidInputWithTheExtractorsExplanation() {
    assertRejected(
        () ->
            resolver.resolve(read(FEED, REJECTING_EXTRACTOR, FIRST_MAPPER, FIRST), AccessMode.READ),
        ConnectionExceptionCodes.CONNECTION_INPUT_INVALID,
        "host");
  }

  @Test
  void rejectsAnIntegrationThatIsNoLongerActive() {
    when(catalog.findSchemaOwner(any()))
        .thenReturn(
            Optional.of(new IntegrationIdentity(FEED.integrationId(), "Feed", "Feed", false)));

    assertRejected(
        () -> resolver.resolve(read(FEED, FIRST_EXTRACTOR, FIRST_MAPPER, FIRST), AccessMode.READ),
        ConnectionExceptionCodes.INTEGRATION_INACTIVE,
        null);
  }

  @Test
  void rejectsAnIntegrationThisRuntimeDoesNotRegister() {
    var unregistered =
        new TestConnectionSchema(
            UUID.fromString("c47d0e92-6a1b-4f3e-8d25-91b0e6f4a7c8"),
            1,
            "Unregistered",
            "Unregistered",
            Set.of(TransportProtocol.TCP));
    when(catalog.findSchemaOwner(any()))
        .thenReturn(
            Optional.of(
                new IntegrationIdentity(
                    unregistered.integrationId(), "Unregistered", "Unregistered", true)));

    assertRejected(
        () ->
            resolver.resolve(
                read(unregistered, FIRST_EXTRACTOR, FIRST_MAPPER, FIRST), AccessMode.READ),
        ConnectionExceptionCodes.INTEGRATION_UNAVAILABLE,
        null);
  }

  /**
   * A writing connection pairs no mapper with its framing, yet still frames as the integration
   * does.
   */
  @Test
  void holdsAWriteOnlyConnectionToTheIntegrationsFramings() {
    assertRejected(
        () ->
            resolver.resolve(
                tcp(
                    FEED,
                    FIRST_EXTRACTOR,
                    null,
                    EGRESS,
                    new TcpFramingSelection.Custom("delimiterSplit", "\n")),
                AccessMode.WRITE),
        ConnectionExceptionCodes.FRAMING_NOT_ALLOWED,
        TcpFramingSelection.FIELD);
    assertRejected(
        () -> resolver.resolve(tcp(FEED, FIRST_EXTRACTOR, null, EGRESS, FOREIGN), AccessMode.WRITE),
        ConnectionExceptionCodes.FRAMING_NOT_ALLOWED,
        TcpFramingSelection.FIELD);
  }

  /** A writing connection decodes nothing, so its framing pairs with no ingress mapper. */
  @Test
  void resolvesAWriteOnlyConnectionWithItsEgressMapperAlone() {
    var setup =
        resolver
            .resolve(tcp(FEED, FIRST_EXTRACTOR, null, EGRESS, SECOND), AccessMode.WRITE)
            .setup();

    assertThat(setup.ingressMapper()).isEmpty();
    assertThat(setup.egressMapper()).contains(new MapperReference(EGRESS, 2));
  }

  /** The access the connection stores is the one the resolution decided, mappers included. */
  @Test
  void answersWithTheAccessTheConnectionStores() {
    var resolution =
        resolver.resolve(
            tcp(FEED, FIRST_EXTRACTOR, FIRST_MAPPER, EGRESS, FIRST), AccessMode.READ_WRITE);

    assertThat(resolution.access()).isEqualTo(new ReadWriteAccess(FIRST_MAPPER, EGRESS));
  }

  @Test
  void resolvesAReadWriteConnectionWithBothMappers() {
    var setup =
        resolver
            .resolve(tcp(FEED, FIRST_EXTRACTOR, FIRST_MAPPER, EGRESS, FIRST), AccessMode.READ_WRITE)
            .setup();

    assertThat(setup.ingressMapper()).contains(new MapperReference(FIRST_MAPPER, 3));
    assertThat(setup.egressMapper()).contains(new MapperReference(EGRESS, 2));
  }

  @Test
  void rejectsAMapperTheAccessModeDoesNotUse() {
    assertRejected(
        () ->
            resolver.resolve(
                tcp(FEED, FIRST_EXTRACTOR, FIRST_MAPPER, EGRESS, FIRST), AccessMode.READ),
        ConnectionExceptionCodes.EGRESS_MAPPER_NOT_ACCEPTED,
        ConnectionCreationDetails.EGRESS_MAPPER_FIELD);
    assertRejected(
        () ->
            resolver.resolve(
                tcp(FEED, FIRST_EXTRACTOR, FIRST_MAPPER, EGRESS, FIRST), AccessMode.WRITE),
        ConnectionExceptionCodes.INGRESS_MAPPER_NOT_ACCEPTED,
        ConnectionCreationDetails.INGRESS_MAPPER_FIELD);
    assertRejected(
        () -> resolver.resolve(tcp(FEED, FIRST_EXTRACTOR, null, null, FIRST), AccessMode.WRITE),
        ConnectionExceptionCodes.EGRESS_MAPPER_REQUIRED,
        ConnectionCreationDetails.EGRESS_MAPPER_FIELD);
    assertRejected(
        () ->
            resolver.resolve(
                tcp(FEED, FIRST_EXTRACTOR, FIRST_MAPPER, null, FIRST), AccessMode.READ_WRITE),
        ConnectionExceptionCodes.EGRESS_MAPPER_REQUIRED,
        ConnectionCreationDetails.EGRESS_MAPPER_FIELD);
  }

  @Test
  void rejectsAnEgressMapperTheIntegrationDoesNotPermitOrCannotOffer() {
    assertRejected(
        () ->
            resolver.resolve(
                tcp(FEED, FIRST_EXTRACTOR, null, OTHER_EGRESS, FIRST), AccessMode.WRITE),
        ConnectionExceptionCodes.MAPPER_NOT_ALLOWED,
        ConnectionCreationDetails.EGRESS_MAPPER_FIELD);
    assertRejected(
        () ->
            resolver.resolve(
                tcp(FEED, FIRST_EXTRACTOR, null, ARCHIVED_EGRESS, FIRST), AccessMode.WRITE),
        ConnectionExceptionCodes.MAPPER_ARCHIVED,
        ConnectionCreationDetails.EGRESS_MAPPER_FIELD);
    mapper(EGRESS, MapperQualifier.INGRESS, false, 2);
    assertRejected(
        () -> resolver.resolve(tcp(FEED, FIRST_EXTRACTOR, null, EGRESS, FIRST), AccessMode.WRITE),
        ConnectionExceptionCodes.MAPPER_NOT_FOUND,
        ConnectionCreationDetails.EGRESS_MAPPER_FIELD);
    when(mappers.findLatest(EGRESS)).thenReturn(Optional.empty());
    assertRejected(
        () -> resolver.resolve(tcp(FEED, FIRST_EXTRACTOR, null, EGRESS, FIRST), AccessMode.WRITE),
        ConnectionExceptionCodes.MAPPER_NOT_FOUND,
        ConnectionCreationDetails.EGRESS_MAPPER_FIELD);
  }

  /** An egress mapper the catalog lists still cannot be used unless an integration provides it. */
  @Test
  void rejectsAnEgressMapperNoIntegrationContributes() {
    var resolverWithoutContributions =
        new TcpConnectionSetupResolver(
            selection(catalog, FEED.reference(), READ_ONLY.reference()),
            new ConnectionMappingRegistry(
                List.of(FEED),
                List.of(
                    new StubConnectionMappingContribution(
                        FEED,
                        Map.of(
                            TransportProtocol.TCP,
                            MapperFramingDeclaration.of(
                                    new MapperFramingPair(FIRST_MAPPER, FIRST_FRAMING))
                                .withEgress(EgressMapperRule.openMapper()))))),
            new ConnectionExtractorRegistry(List.of(FEED), List.of(first)),
            new ConnectionMapperSelection(mappers, EgressMapperContributions.of(List.of())),
            certificateStores);

    assertRejected(
        () ->
            resolverWithoutContributions.resolve(
                tcp(FEED, FIRST_EXTRACTOR, null, EGRESS, FIRST), AccessMode.WRITE),
        ConnectionExceptionCodes.MAPPER_NOT_FOUND,
        ConnectionCreationDetails.EGRESS_MAPPER_FIELD);
  }

  @Test
  void rejectsWritingThroughAnIntegrationWithoutAnEgressRule() {
    assertRejected(
        () ->
            resolver.resolve(
                tcp(READ_ONLY, READ_ONLY_EXTRACTOR, null, EGRESS, FIRST), AccessMode.WRITE),
        ConnectionExceptionCodes.UNSUPPORTED_ACCESS_MODE,
        null);
  }

  private void mapper(UUID id, MapperQualifier qualifier, boolean archived, int version) {
    when(mappers.findLatest(id))
        .thenReturn(
            Optional.of(
                MapperProfile.builder()
                    .withId(id)
                    .withVersion(version)
                    .withQualifier(qualifier)
                    .withArchived(archived)
                    .build()));
  }

  private static PayloadEgressMapper egressMapper(UUID id) {
    var mapper = mock(PayloadEgressMapper.class);
    when(mapper.id()).thenReturn(id);
    return mapper;
  }

  private static void assertRejected(
      ThrowingCallable resolution, ConnectionExceptionCodes code, String field) {
    assertThatThrownBy(resolution)
        .isInstanceOfSatisfying(
            GlobalOpenCopException.class,
            exception -> {
              assertThat(exception.getCode()).isEqualTo(code.code());
              assertThat(exception.getField()).isEqualTo(field);
            });
  }

  private static void assertUnreadableStore(ThrowingCallable resolution, String message) {
    assertThatThrownBy(resolution)
        .isInstanceOfSatisfying(
            GlobalOpenCopException.class,
            exception -> {
              assertThat(exception.getCode())
                  .isEqualTo(ConnectionExceptionCodes.CONNECTION_INPUT_INVALID.code());
              assertThat(exception.getDetailCode())
                  .isEqualTo(TcpConnectionSetupResolver.UNREADABLE_CERTIFICATE_STORE);
              assertThat(exception.getMessage()).isEqualTo(message);
            });
  }

  private static CertificateMaterial withPassword(String password) {
    return argThat(certificate -> certificate != null && password.equals(certificate.password()));
  }

  private static ConnectionCreation.Tcp read(
      TestConnectionSchema schema, UUID extractorId, UUID mapperId, TcpFramingSelection framing) {
    return tcp(schema, extractorId, mapperId, null, framing);
  }

  private static ConnectionCreation.Tcp tcp(
      TestConnectionSchema schema,
      UUID extractorId,
      UUID mapperId,
      UUID egressMapperId,
      TcpFramingSelection framing) {
    return new ConnectionCreation.Tcp(
        new ConnectionCreationDetails(
            "Feed",
            "R",
            schema.reference(),
            extractorId,
            new DeclaredConnectionInput(List.of(), List.of()),
            mapperId,
            ACTOR,
            null),
        framing,
        egressMapperId);
  }

  /**
   * An extractor answering every request with one endpoint, or rejecting every request when it has
   * none. It refuses to be selected by shape, so a resolution that tried would fail.
   */
  private static final class FixedExtractor implements TcpConnectionExtractorCapability {

    private final IntegrationConnectionSchema schema;
    private final UUID id;
    private final String host;
    private final AtomicInteger extractions = new AtomicInteger();

    FixedExtractor(IntegrationConnectionSchema schema, UUID id, String host) {
      this.schema = schema;
      this.id = id;
      this.host = host;
    }

    @Override
    public IntegrationConnectionSchema schema() {
      return schema;
    }

    @Override
    public UUID id() {
      return id;
    }

    @Override
    public ExtractorDescriptor descriptor() {
      return new ExtractorDescriptor(
          id, "Fixed " + id, List.of(StandardExtractorField.HOST.declare(true)));
    }

    @Override
    public void validate(TcpConnectionInput input) throws ConnectionInputException {
      if (host == null) {
        throw ConnectionInputException.rejecting(
            "host", ConnectionInputFailure.INVALID_INPUT, "MISSING_HOST", "A host is required");
      }
    }

    @Override
    public TcpConnectionMaterial extract(TcpConnectionInput input) {
      extractions.incrementAndGet();
      return TcpConnectionMaterial.plain(new TcpEndpointMaterial(host, 8089));
    }
  }

  /** Produces mutual TLS material with a client store and a truststore, both left unopened. */
  private static final class MtlsExtractor implements TcpConnectionExtractorCapability {

    private final IntegrationConnectionSchema schema;
    private final UUID id;

    MtlsExtractor(IntegrationConnectionSchema schema, UUID id) {
      this.schema = schema;
      this.id = id;
    }

    @Override
    public IntegrationConnectionSchema schema() {
      return schema;
    }

    @Override
    public UUID id() {
      return id;
    }

    @Override
    public ExtractorDescriptor descriptor() {
      return new ExtractorDescriptor(
          id, "Mutual TLS " + id, List.of(StandardExtractorField.HOST.declare(true)));
    }

    @Override
    public void validate(TcpConnectionInput input) {
      // Accepts any input: the stores it produces are the host's to check.
    }

    @Override
    public TcpConnectionMaterial extract(TcpConnectionInput input) {
      return TcpConnectionMaterial.mtls(
          new TcpEndpointMaterial("mtls.test", 8089),
          new MtlsCredentialMaterial(store(CLIENT_PASSWORD), Optional.of(store(CA_PASSWORD))));
    }

    private static CertificateMaterial store(String password) {
      return new CertificateMaterial(
          new DetachedBinaryMaterial(new byte[] {1}), password, CertificateStoreFormat.PKCS12);
    }
  }
}
