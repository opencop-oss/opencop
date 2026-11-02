// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.extension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.ports.gateway.authorization.ConnectionAuthorization;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionExceptionCodes;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionManagementException;
import com.opencop.core.api.general.ports.usecase.connection.extension.ConnectionExtensionSummary;
import com.opencop.core.api.general.ports.usecase.connection.extension.ExtensionOperationSummary;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchema;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.business.model.integration.connection.extension.AccessLevel;
import com.opencop.core.sdk.business.model.integration.connection.extension.ExtensionBody;
import com.opencop.core.sdk.business.model.integration.connection.extension.ExtensionDescriptor;
import com.opencop.core.sdk.business.model.integration.connection.extension.ExtensionMethod;
import com.opencop.core.sdk.business.model.integration.connection.settings.NoConnectionSettings;
import com.opencop.core.sdk.business.registry.integration.connection.extension.ConnectionExtensionRegistry;
import com.opencop.core.sdk.business.registry.integration.connection.settings.ConnectionSettingsRegistry;
import com.opencop.core.sdk.model.connection.spec.ConnectionSpec;
import com.opencop.core.sdk.model.connection.spec.transport.UdpConfig;
import com.opencop.core.sdk.model.datasource.AccessMode;
import com.opencop.core.sdk.model.datasource.SocketDataSource;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.ports.integration.connection.extension.ConnectionExtensionCapability;
import com.opencop.core.sdk.ports.integration.connection.extension.ExtensionOperation;
import com.opencop.core.sdk.testing.integration.connection.behavior.TestConnectionSchema;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ListConnectionExtensionsUsecaseTest {

  private static final UUID SOURCE_ID = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
  private static final UUID USER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000b1");
  private static final TestConnectionSchema SCHEMA =
      new TestConnectionSchema(
          UUID.fromString("0f7b8e7c-49b8-4d0e-8f43-4bb1b0f0a1c2"), 1, "Test", "Test integration");
  private static final IntegrationConnectionSchemaReference UNREGISTERED =
      new IntegrationConnectionSchemaReference(
          UUID.fromString("6c1f0f6e-0c38-4a5b-9c0e-3f4d2b8a7e91"), 1);
  private static final ExtensionDescriptor DESCRIPTOR =
      new ExtensionDescriptor("Data sync", "Synchronises remote data");
  private static final ExtensionOperationSummary LIST_MISSIONS =
      new ExtensionOperationSummary(ExtensionMethod.GET, "/missions", AccessLevel.READ);
  private static final ExtensionOperationSummary SUBSCRIBE =
      new ExtensionOperationSummary(
          ExtensionMethod.PUT, "/subscriptions/{name}", AccessLevel.MANAGE);

  private final DataSourceRepository dataSources = mock(DataSourceRepository.class);
  private final ConnectionAuthorization authorization = mock(ConnectionAuthorization.class);
  private final ListConnectionExtensionsUsecase usecase = usecase();

  @Test
  void aViewerSeesOnlyTheOperationsThatRead() {
    givenConnection(SCHEMA.reference(), null);

    assertThat(usecase.list(SOURCE_ID, USER_ID, false))
        .containsExactly(
            new ConnectionExtensionSummary(
                "data-sync", DESCRIPTOR.label(), DESCRIPTOR.description(), List.of(LIST_MISSIONS)));
  }

  @Test
  void anOwnerOrGlobalAdminSeesEveryOperation() {
    givenConnection(SCHEMA.reference(), null);
    when(authorization.isDirectOwnerOrAdmin(SOURCE_ID, USER_ID, false)).thenReturn(true);

    assertThat(usecase.list(SOURCE_ID, USER_ID, false))
        .extracting(ConnectionExtensionSummary::operations)
        .containsExactly(List.of(LIST_MISSIONS, SUBSCRIBE), List.of(SUBSCRIBE));
  }

  @Test
  void anExtensionTheUserCanInvokeNothingOfIsLeftOut() {
    givenConnection(SCHEMA.reference(), null);

    assertThat(usecase.list(SOURCE_ID, USER_ID, false))
        .extracting(ConnectionExtensionSummary::id)
        .containsExactly("data-sync");
  }

  @Test
  void aConnectionTheUserCannotSeeIsNotFound() {
    givenConnection(SCHEMA.reference(), null);
    doThrow(ConnectionManagementException.connectionNotFound(SOURCE_ID))
        .when(authorization)
        .assertCanRead(SOURCE_ID, USER_ID, false);

    assertStatus(HttpStatus.NOT_FOUND);
  }

  @Test
  void anArchivedOrMissingConnectionIsNotFound() {
    givenConnection(SCHEMA.reference(), Instant.parse("2026-10-01T00:00:00Z"));
    assertStatus(HttpStatus.NOT_FOUND);

    when(dataSources.findById(SOURCE_ID)).thenReturn(Optional.empty());
    assertStatus(HttpStatus.NOT_FOUND);
  }

  @Test
  void anIntegrationThisRuntimeDoesNotRegisterIsUnavailable() {
    givenConnection(UNREGISTERED, null);

    assertThatThrownBy(() -> usecase.list(SOURCE_ID, USER_ID, false))
        .isInstanceOfSatisfying(
            GlobalOpenCopException.class,
            error ->
                assertThat(error.getCode())
                    .isEqualTo(ConnectionExceptionCodes.INTEGRATION_UNAVAILABLE.code()));
  }

  @Test
  void aConnectionWithoutAnIntegrationHasNoExtensions() {
    givenConnection(null, null);

    assertThat(usecase.list(SOURCE_ID, USER_ID, false)).isEmpty();
  }

  private ListConnectionExtensionsUsecase usecase() {
    List<IntegrationConnectionSchema> schemas = List.of(SCHEMA);
    var settings = new ConnectionSettingsRegistry(schemas, List.of());
    var extensions =
        new ConnectionExtensionRegistry(
            schemas,
            List.of(
                extension(
                    "data-sync",
                    operation(ExtensionMethod.GET, "/missions", AccessLevel.READ),
                    operation(ExtensionMethod.PUT, "/subscriptions/{name}", AccessLevel.MANAGE)),
                extension(
                    "manage-only",
                    operation(ExtensionMethod.PUT, "/subscriptions/{name}", AccessLevel.MANAGE))),
            settings);
    return new ListConnectionExtensionsUsecase(
        new ExtensionConnections(dataSources, authorization, schemas), authorization, extensions);
  }

  private void givenConnection(IntegrationConnectionSchemaReference integration, Instant archived) {
    when(dataSources.findById(SOURCE_ID))
        .thenReturn(
            Optional.of(
                new SocketDataSource(
                    SOURCE_ID,
                    "connection",
                    "tenant",
                    new ConnectionSpec(new UdpConfig("remote.example", 4242, null)),
                    null,
                    null,
                    integration,
                    AccessMode.READ,
                    true,
                    null,
                    archived,
                    archived == null ? null : USER_ID,
                    null,
                    null,
                    null,
                    null,
                    null,
                    true)));
  }

  private void assertStatus(HttpStatus status) {
    assertThatThrownBy(() -> usecase.list(SOURCE_ID, USER_ID, false))
        .isInstanceOfSatisfying(
            GlobalOpenCopException.class,
            error -> assertThat(error.getHttpStatus()).isEqualTo(status));
  }

  @SafeVarargs
  private static ConnectionExtensionCapability<NoConnectionSettings> extension(
      String id, ExtensionOperation<NoConnectionSettings, ?, ?>... operations) {
    return new ConnectionExtensionCapability<>() {
      @Override
      public IntegrationConnectionSchema schema() {
        return SCHEMA;
      }

      @Override
      public Class<NoConnectionSettings> settingsType() {
        return NoConnectionSettings.class;
      }

      @Override
      public String id() {
        return id;
      }

      @Override
      public ExtensionDescriptor descriptor() {
        return DESCRIPTOR;
      }

      @Override
      public List<ExtensionOperation<NoConnectionSettings, ?, ?>> operations() {
        return List.of(operations);
      }
    };
  }

  private static ExtensionOperation<NoConnectionSettings, Void, Void> operation(
      ExtensionMethod method, String path, AccessLevel access) {
    return ExtensionOperation.of(
        method,
        path,
        access,
        ExtensionBody.none(),
        ExtensionBody.none(),
        (context, request) -> null);
  }
}
