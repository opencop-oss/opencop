// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.business.service.ConnectRoutingTargetResolver;
import com.opencop.core.api.general.business.service.DataSourceRuntimeService;
import com.opencop.core.sdk.business.model.integration.Integration;
import com.opencop.core.sdk.business.model.integration.mapping.egress.EgressMapperContributions;
import com.opencop.core.sdk.business.model.integration.mapping.egress.IntegrationEgressMappers;
import com.opencop.core.sdk.business.model.profile.MapperProfile;
import com.opencop.core.sdk.business.model.profile.MapperQualifier;
import com.opencop.core.sdk.model.connection.add.TransportKind;
import com.opencop.core.sdk.model.connection.spec.ConnectionSpec;
import com.opencop.core.sdk.model.connection.spec.security.NoSecurity;
import com.opencop.core.sdk.model.connection.spec.transport.RtspConfig;
import com.opencop.core.sdk.model.connection.spec.transport.TcpConfig;
import com.opencop.core.sdk.model.connection.spec.transport.TransportConfig;
import com.opencop.core.sdk.model.connection.spec.transport.UdpConfig;
import com.opencop.core.sdk.model.datasource.AccessMode;
import com.opencop.core.sdk.model.datasource.DataSourceRuntime;
import com.opencop.core.sdk.model.datasource.RuntimeStatus;
import com.opencop.core.sdk.model.datasource.SocketDataSource;
import com.opencop.core.sdk.model.visibility.RecordVisibility;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.ports.gateway.api.ConnectService;
import com.opencop.core.sdk.ports.gateway.iam.AccessRepository;
import com.opencop.core.sdk.ports.gateway.mapper.MapperProfileService;
import com.opencop.core.sdk.ports.gateway.mapping.egress.PayloadEgressMapper;
import com.opencop.core.sdk.ports.gateway.mapping.ingress.MappingException;
import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import com.opencop.core.sdk.testing.integration.connection.behavior.TestConnectionSchema.TestIntegration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.geojson.Feature;
import org.geojson.FeatureCollection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

class PushFeaturesUsecaseTest {

  private static final UUID SOURCE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655443001");
  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655443002");
  private static final UUID MAPPER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655443004");

  private static final UUID EGRESS_MAPPER_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655443005");
  private static final Integration INTEGRATION =
      new TestIntegration(
          UUID.fromString("550e8400-e29b-41d4-a716-446655443006"),
          "Test integration",
          "Test integration");
  private static final String OWNER_INSTANCE = "connect-assigned";

  private PayloadEgressMapper egressMapper;
  private MapperProfileService mappers;
  private ConnectService connectService;
  private DataSourceRuntimeService runtimeService;
  private ConnectRoutingTargetResolver routingTargetResolver;
  private AccessRepository<ServerRole> sourceAccess;
  private PushFeaturesUsecase usecase;

  @BeforeEach
  void setUp() {
    egressMapper = mock(PayloadEgressMapper.class);
    when(egressMapper.id()).thenReturn(EGRESS_MAPPER_ID);
    when(egressMapper.name()).thenReturn("Test egress");
    when(egressMapper.version()).thenReturn(1);
    mappers = mock(MapperProfileService.class);
    connectService = mock(ConnectService.class);
    runtimeService = mock(DataSourceRuntimeService.class);
    routingTargetResolver = mock(ConnectRoutingTargetResolver.class);
    sourceAccess = mock(AccessRepository.class);
    usecase =
        new PushFeaturesUsecase(
            EgressMapperContributions.of(
                List.of(new IntegrationEgressMappers(INTEGRATION, List.of(egressMapper)))),
            mappers,
            connectService,
            runtimeService,
            routingTargetResolver,
            sourceAccess);
  }

  @ParameterizedTest
  @CsvSource({"W", "RW"})
  @SuppressWarnings("unchecked")
  void execute_encodesEveryFeatureWithTheSourcesEgressMapperAndSendsTheBatch(String access)
      throws Exception {
    var first = feature("first");
    var second = feature("second");
    givenWritableSource(AccessMode.fromString(access));
    givenStoredEgressMapper(egressProfile(false));
    when(egressMapper.map(first)).thenReturn(new byte[] {1});
    when(egressMapper.map(second)).thenReturn(new byte[] {2});

    usecase.execute(SOURCE_ID, USER_ID, features(first, second));

    ArgumentCaptor<List<byte[]>> sent = ArgumentCaptor.forClass(List.class);
    verify(connectService).pushOnInstance(eq(SOURCE_ID), sent.capture(), eq(OWNER_INSTANCE));
    assertThat(sent.getValue()).containsExactly(new byte[] {1}, new byte[] {2});
  }

  @Test
  void execute_whenTheEgressMapperIsArchived_rejectsBeforeEncodingOrPush() throws Exception {
    givenWritableSource(AccessMode.WRITE);
    givenStoredEgressMapper(egressProfile(true));

    var exception =
        assertThrows(
            GlobalOpenCopException.class,
            () -> usecase.execute(SOURCE_ID, USER_ID, features(new Feature())));

    assertEquals("MAPPER_ARCHIVED", exception.getCode());
    verify(egressMapper, never()).map(any());
    verifyNoInteractions(connectService);
  }

  @Test
  void execute_whenNoIntegrationContributesTheStoredEgressMapper_rejectsBeforePush() {
    var unregistered = UUID.randomUUID();
    givenWritableSource(AccessMode.WRITE, unregistered);
    when(mappers.findLatest(unregistered))
        .thenReturn(Optional.of(egressProfile(false).toBuilder().withId(unregistered).build()));

    var exception =
        assertThrows(
            GlobalOpenCopException.class,
            () -> usecase.execute(SOURCE_ID, USER_ID, features(new Feature())));

    assertEquals("MAPPER_NOT_FOUND", exception.getCode());
    verifyNoInteractions(connectService);
  }

  @Test
  void execute_whenOneFeatureFailsToEncode_sendsNothing() throws Exception {
    var encodable = feature("encodable");
    var unencodable = feature("unencodable");
    givenWritableSource(AccessMode.READ_WRITE);
    givenStoredEgressMapper(egressProfile(false));
    when(egressMapper.map(encodable)).thenReturn(new byte[] {1});
    when(egressMapper.map(unencodable)).thenThrow(new IllegalArgumentException("unsupported"));

    assertThrows(
        IllegalArgumentException.class,
        () -> usecase.execute(SOURCE_ID, USER_ID, features(encodable, unencodable)));

    verifyNoInteractions(connectService);
  }

  /** A feature the egress mapper declines is the client's to fix, so nothing is sent. */
  @Test
  void execute_whenTheEgressMapperDeclinesAFeature_rejectsItWithTheMappersCode() throws Exception {
    var encodable = feature("encodable");
    var declined = feature("declined");
    givenWritableSource(AccessMode.READ_WRITE);
    givenStoredEgressMapper(egressProfile(false));
    when(egressMapper.map(encodable)).thenReturn(new byte[] {1});
    when(egressMapper.map(declined))
        .thenThrow(
            new MappingException(
                MappingException.Code.INVALID_OUTBOUND_MAPPING, "expected a SYMBOL geometry"));

    var exception =
        assertThrows(
            GlobalOpenCopException.class,
            () -> usecase.execute(SOURCE_ID, USER_ID, features(encodable, declined)));

    assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
    assertEquals("INVALID_OUTBOUND_MAPPING", exception.getCode());
    assertEquals("expected a SYMBOL geometry", exception.getMessage());
    verifyNoInteractions(connectService);
  }

  private void givenWritableSource(AccessMode access) {
    givenWritableSource(access, EGRESS_MAPPER_ID);
  }

  private void givenWritableSource(AccessMode access, UUID egressMapperId) {
    when(sourceAccess.userRole(USER_ID, SOURCE_ID)).thenReturn(ServerRole.ADMIN);
    when(runtimeService.getWithRuntime(SOURCE_ID, RecordVisibility.ALL))
        .thenReturn(
            runtime(access, TransportKind.TCP, egressMapperId, OWNER_INSTANCE, OWNER_INSTANCE));
    when(routingTargetResolver.resolveKnownOwnerTarget(SOURCE_ID, OWNER_INSTANCE, "PUSH"))
        .thenReturn(Optional.of(OWNER_INSTANCE));
  }

  private void givenStoredEgressMapper(MapperProfile catalogEntry) {
    when(mappers.findLatest(EGRESS_MAPPER_ID)).thenReturn(Optional.of(catalogEntry));
  }

  private static MapperProfile egressProfile(boolean archived) {
    return MapperProfile.builder()
        .withId(EGRESS_MAPPER_ID)
        .withName("Test egress")
        .withQualifier(MapperQualifier.EGRESS)
        .withVersion(1)
        .withArchived(archived)
        .build();
  }

  private static Feature feature(String id) {
    var feature = new Feature();
    feature.setId(id);
    return feature;
  }

  private static FeatureCollection features(Feature... features) {
    var collection = new FeatureCollection();
    collection.setFeatures(List.of(features));
    return collection;
  }

  @Test
  void execute_whenUserHasNoRole_throwsForbidden() {
    when(sourceAccess.userRole(USER_ID, SOURCE_ID)).thenReturn(null);

    var exception =
        assertThrows(
            GlobalOpenCopException.class,
            () -> usecase.execute(SOURCE_ID, USER_ID, new FeatureCollection()));

    assertEquals(HttpStatus.FORBIDDEN, exception.getHttpStatus());
    verifyNoInteractions(connectService);
  }

  @Test
  void execute_whenUserIsViewer_throwsForbidden() {
    when(sourceAccess.userRole(USER_ID, SOURCE_ID)).thenReturn(ServerRole.VIEWER);

    var exception =
        assertThrows(
            GlobalOpenCopException.class,
            () -> usecase.execute(SOURCE_ID, USER_ID, new FeatureCollection()));

    assertEquals(HttpStatus.FORBIDDEN, exception.getHttpStatus());
  }

  @Test
  void execute_whenSourceIsReadOnly_throwsForbidden() {
    when(sourceAccess.userRole(USER_ID, SOURCE_ID)).thenReturn(ServerRole.ADMIN);
    when(runtimeService.getWithRuntime(SOURCE_ID, RecordVisibility.ALL))
        .thenReturn(runtime(AccessMode.READ, "connect-assigned", "connect-assigned"));

    var exception =
        assertThrows(
            GlobalOpenCopException.class,
            () -> usecase.execute(SOURCE_ID, USER_ID, new FeatureCollection()));

    assertEquals(HttpStatus.FORBIDDEN, exception.getHttpStatus());
  }

  @ParameterizedTest
  @CsvSource({"UDP,W", "UDP,RW", "RTSP,W", "RTSP,RW"})
  void execute_whenTheTransportDoesNotAllowTheStoredAccess_rejectsBeforeRoutingMappingOrPush(
      TransportKind transport, String access) {
    when(sourceAccess.userRole(USER_ID, SOURCE_ID)).thenReturn(ServerRole.ADMIN);
    when(runtimeService.getWithRuntime(SOURCE_ID, RecordVisibility.ALL))
        .thenReturn(
            runtime(
                AccessMode.fromString(access), transport, "connect-assigned", "connect-assigned"));

    var exception =
        assertThrows(
            GlobalOpenCopException.class,
            () -> usecase.execute(SOURCE_ID, USER_ID, new FeatureCollection()));

    assertEquals("CORE-CONNECTION-1006", exception.getCode());
    verifyNoInteractions(routingTargetResolver, mappers, connectService);
  }

  @ParameterizedTest
  @CsvSource({"W", "RW"})
  void execute_whenTheSourceHasNoEgressMapper_rejectsBeforeRoutingOrPush(String access) {
    var features = new FeatureCollection();
    features.setFeatures(List.of(new Feature()));
    when(sourceAccess.userRole(USER_ID, SOURCE_ID)).thenReturn(ServerRole.ADMIN);
    when(runtimeService.getWithRuntime(SOURCE_ID, RecordVisibility.ALL))
        .thenReturn(runtime(AccessMode.fromString(access), "connect-assigned", "connect-assigned"));

    var exception =
        assertThrows(
            GlobalOpenCopException.class, () -> usecase.execute(SOURCE_ID, USER_ID, features));

    assertEquals("EGRESS_MAPPER_REQUIRED", exception.getCode());
    assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
    verifyNoInteractions(routingTargetResolver, connectService);
  }

  @Test
  void execute_whenSourceIsArchived_throwsConflictBeforeRouteResolutionOrEncodingOrPush() {
    when(sourceAccess.userRole(USER_ID, SOURCE_ID)).thenReturn(ServerRole.ADMIN);
    var archivedDataSource =
        runtimeArchived(AccessMode.READ_WRITE, "connect-assigned", "connect-assigned");
    when(runtimeService.getWithRuntime(SOURCE_ID, RecordVisibility.ALL))
        .thenReturn(archivedDataSource);

    var exception =
        assertThrows(
            GlobalOpenCopException.class,
            () -> usecase.execute(SOURCE_ID, USER_ID, new FeatureCollection()));

    assertEquals(HttpStatus.CONFLICT, exception.getHttpStatus());
    verify(runtimeService).getWithRuntime(SOURCE_ID, RecordVisibility.ALL);
    verifyNoInteractions(routingTargetResolver, mappers, connectService);
  }

  /** A source stored without an egress mapper. */
  private static DataSourceRuntime runtime(
      AccessMode accessMode, String assignedInstance, String runtimeInstance) {
    return runtime(accessMode, TransportKind.TCP, null, assignedInstance, runtimeInstance);
  }

  private static DataSourceRuntime runtime(
      AccessMode accessMode,
      TransportKind transport,
      String assignedInstance,
      String runtimeInstance) {
    return runtime(accessMode, transport, null, assignedInstance, runtimeInstance);
  }

  private static DataSourceRuntime runtime(
      AccessMode accessMode,
      TransportKind transport,
      UUID egressMapperId,
      String assignedInstance,
      String runtimeInstance) {
    TransportConfig transportConfig =
        switch (transport) {
          case TCP -> new TcpConfig("localhost", 4242, null, null, null, null, new NoSecurity());
          case UDP -> new UdpConfig("localhost", 4242, null);
          case RTSP -> new RtspConfig("rtsp://localhost:4242/stream", null);
          case KAFKA -> throw new IllegalArgumentException("Kafka is not a socket transport");
        };
    var spec = new ConnectionSpec(transportConfig);
    var dataSource =
        new SocketDataSource(
            SOURCE_ID,
            "Alpha",
            "tenant",
            spec,
            MAPPER_ID,
            egressMapperId,
            null,
            accessMode,
            true,
            assignedInstance,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            true);
    return new DataSourceRuntime(
        dataSource,
        new RuntimeStatus(
            SOURCE_ID,
            com.opencop.core.sdk.ports.gateway.socket.ConnectorState.CONNECTED,
            Instant.now(),
            runtimeInstance));
  }

  private static DataSourceRuntime runtimeArchived(
      AccessMode accessMode, String assignedInstance, String runtimeInstance) {
    var spec =
        new ConnectionSpec(
            new TcpConfig("localhost", 4242, null, null, null, null, new NoSecurity()));
    var dataSource =
        new SocketDataSource(
            SOURCE_ID,
            "Alpha",
            "tenant",
            spec,
            null,
            null,
            null,
            accessMode,
            true,
            assignedInstance,
            Instant.parse("2026-05-27T12:00:00Z"),
            UUID.fromString("550e8400-e29b-41d4-a716-446655443003"),
            null,
            null,
            null,
            null,
            null,
            true);
    return new DataSourceRuntime(
        dataSource,
        new RuntimeStatus(
            SOURCE_ID,
            com.opencop.core.sdk.ports.gateway.socket.ConnectorState.CONNECTED,
            Instant.now(),
            runtimeInstance));
  }
}
