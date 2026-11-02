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
import com.opencop.core.sdk.business.model.integration.mapping.egress.EgressMapperContributions;
import com.opencop.core.sdk.business.model.profile.MapperProfile;
import com.opencop.core.sdk.business.model.profile.MapperQualifier;
import com.opencop.core.sdk.business.registry.integration.connection.input.ConnectionExtractorRegistry;
import com.opencop.core.sdk.business.registry.integration.connection.mapping.ConnectionMappingRegistry;
import com.opencop.core.sdk.business.registry.integration.connection.settings.ConnectionSettingsRegistry;
import com.opencop.core.sdk.integration.builtin.business.model.NativeConnectionMappingContribution;
import com.opencop.core.sdk.integration.builtin.business.model.NativeConnectionSchema;
import com.opencop.core.sdk.integration.builtin.business.model.NativeIntegration;
import com.opencop.core.sdk.integration.builtin.business.model.NativeMappers;
import com.opencop.core.sdk.integration.builtin.business.policy.rtsp.RtspCoordinatesPolicy;
import com.opencop.core.sdk.integration.builtin.business.policy.rtsp.RtspUrlPolicy;
import com.opencop.core.sdk.integration.builtin.technical.extractor.rtsp.NativeRtspStreamExtractor;
import com.opencop.core.sdk.integration.builtin.technical.extractor.tcp.NativePlainTcpExtractor;
import com.opencop.core.sdk.integration.builtin.technical.mapping.ingress.KlvIngressMapperV1;
import com.opencop.core.sdk.model.connection.spec.transport.RtspConfig;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.ports.gateway.mapper.MapperProfileService;
import com.opencop.core.sdk.ports.gateway.secrets.ConnectionStore;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.geojson.LngLatAlt;
import org.geojson.Point;
import org.junit.jupiter.api.Test;

class RtspConnectionSetupResolutionTest {

  private static final UUID USER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000c1");
  private static final Caller ACTOR = TestActors.of(USER_ID);
  private static final UUID SOURCE_ID = UUID.fromString("00000000-0000-4000-8000-0000000000d1");
  private static final String URL = "rtsp://camera.example.test/live";
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
                      new NativeRtspStreamExtractor(NATIVE), new NativePlainTcpExtractor(NATIVE)))),
          new ConnectionSettingsRegistry(List.of(NATIVE), List.of()),
          mock(ConnectionStore.class));

  @Test
  void resolvesAStreamDecodedWithTheKlvMapperAtTheCamerasPosition() {
    when(mappers.findLatest(NativeMappers.KLV_INGRESS)).thenReturn(Optional.of(klvMapper(false)));

    var setup = resolution.resolve(creation(NativeMappers.KLV_INGRESS), SOURCE_ID);

    assertThat(setup.integration()).isEqualTo(NativeConnectionSchema.V1_REFERENCE);
    assertThat(setup.access()).isEqualTo(new ReadAccess(NativeMappers.KLV_INGRESS));
    assertThat(setup.spec().transport())
        .isEqualTo(new RtspConfig(URL, new Point(new LngLatAlt(-73.6, 45.5))));
    assertThat(setup.prefill().values())
        .containsEntry(RtspUrlPolicy.URL_FIELD, URL)
        .containsEntry(RtspCoordinatesPolicy.LATITUDE_FIELD, "45.5")
        .containsEntry(RtspCoordinatesPolicy.LONGITUDE_FIELD, "-73.6");
  }

  /** An RTSP stream carries KLV metadata, which no other mapper decodes. */
  @Test
  void rejectsAnyMapperButKlv() {
    assertRejected(
        () -> resolution.resolve(creation(UUID.randomUUID()), SOURCE_ID),
        ConnectionExceptionCodes.MAPPER_NOT_ALLOWED.code());
  }

  /** Native imposes the KLV mapper, so its absence is the platform's failure, not the caller's. */
  @Test
  void answersAnUnavailableKlvMapperAsAServerFailure() {
    when(mappers.findLatest(NativeMappers.KLV_INGRESS)).thenReturn(Optional.of(klvMapper(true)));

    assertThatThrownBy(() -> resolution.resolve(creation(NativeMappers.KLV_INGRESS), SOURCE_ID))
        .isInstanceOfSatisfying(
            GlobalOpenCopException.class,
            exception -> {
              assertThat(exception.getCode())
                  .isEqualTo(ConnectionExceptionCodes.SYSTEM_MAPPER_UNAVAILABLE.code());
              assertThat(exception.getHttpStatus().is5xxServerError()).isTrue();
            });
  }

  @Test
  void refusesToWriteBecauseAnRtspConnectionOnlyConsumes() {
    assertRejected(
        () -> resolution.resolve(creation(NativeMappers.KLV_INGRESS, "RW"), SOURCE_ID),
        ConnectionExceptionCodes.UNSUPPORTED_ACCESS_MODE.code());
  }

  @Test
  void rejectsAnUnusableUrlBeforeStoringAnything() {
    when(mappers.findLatest(NativeMappers.KLV_INGRESS)).thenReturn(Optional.of(klvMapper(false)));
    var badUrl =
        new ConnectionCreation.Rtsp(
            details(
                NativeMappers.KLV_INGRESS,
                "R",
                List.of(
                    new ConnectionInputValue(
                        RtspUrlPolicy.URL_FIELD, "http://camera.example.test"))));

    assertThatThrownBy(() -> resolution.resolve(badUrl, SOURCE_ID))
        .isInstanceOfSatisfying(
            GlobalOpenCopException.class,
            exception -> {
              assertThat(exception.getCode())
                  .isEqualTo(ConnectionExceptionCodes.CONNECTION_INPUT_INVALID.code());
              assertThat(exception.getField()).isEqualTo(RtspUrlPolicy.URL_FIELD);
            });
  }

  private static void assertRejected(Runnable creation, String code) {
    assertThatThrownBy(creation::run)
        .isInstanceOfSatisfying(
            GlobalOpenCopException.class,
            exception -> assertThat(exception.getCode()).isEqualTo(code));
  }

  private static ConnectionCreation.Rtsp creation(UUID mapperId) {
    return creation(mapperId, "R");
  }

  private static ConnectionCreation.Rtsp creation(UUID mapperId, String access) {
    return new ConnectionCreation.Rtsp(
        details(
            mapperId,
            access,
            List.of(
                new ConnectionInputValue(RtspUrlPolicy.URL_FIELD, URL),
                new ConnectionInputValue(RtspCoordinatesPolicy.LATITUDE_FIELD, "45.5"),
                new ConnectionInputValue(RtspCoordinatesPolicy.LONGITUDE_FIELD, "-73.6"))));
  }

  private static ConnectionCreationDetails details(
      UUID mapperId, String access, List<ConnectionInputValue> values) {
    return new ConnectionCreationDetails(
        "Alpha RTSP",
        access,
        NativeConnectionSchema.V1_REFERENCE,
        NativeRtspStreamExtractor.EXTRACTOR_ID,
        new DeclaredConnectionInput(values, List.of()),
        mapperId,
        ACTOR,
        null);
  }

  private static MapperProfile klvMapper(boolean archived) {
    return MapperProfile.builder()
        .withId(NativeMappers.KLV_INGRESS)
        .withName(KlvIngressMapperV1.MAPPER_NAME)
        .withQualifier(MapperQualifier.INGRESS)
        .withArchived(archived)
        .build();
  }
}
