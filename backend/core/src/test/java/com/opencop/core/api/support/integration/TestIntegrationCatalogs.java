// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.support.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationIdentity;
import com.opencop.core.api.general.business.usecase.connection.integration.ConnectionIntegrationSelection;
import com.opencop.core.api.general.ports.gateway.integrationcatalog.IntegrationCatalogGateway;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchema;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.integration.builtin.business.model.NativeIntegration;
import java.util.Arrays;
import java.util.Optional;

/** Integration catalogs for tests that create connections without exercising the catalog. */
public final class TestIntegrationCatalogs {

  private TestIntegrationCatalogs() {}

  /**
   * A catalog offering every schema version it is asked about, locked or not, from an active
   * integration.
   */
  public static IntegrationCatalogGateway nativeIsActive() {
    var catalog = mock(IntegrationCatalogGateway.class);
    var owner =
        Optional.of(new IntegrationIdentity(NativeIntegration.NATIVE_ID, "Native", "Native", true));
    when(catalog.findSchemaOwner(any())).thenReturn(owner);
    when(catalog.findSchemaOwnerForUpdate(any())).thenReturn(owner);
    return catalog;
  }

  /**
   * A catalog offering every schema version it is asked about from an active integration, for tests
   * that never lock the integration.
   */
  public static IntegrationCatalogGateway offersEverySchemaActive() {
    var catalog = mock(IntegrationCatalogGateway.class);
    when(catalog.findSchemaOwner(any()))
        .thenReturn(
            Optional.of(
                new IntegrationIdentity(NativeIntegration.NATIVE_ID, "Native", "Native", true)));
    return catalog;
  }

  /** A selection over the catalog, in a runtime registering exactly the given schema versions. */
  public static ConnectionIntegrationSelection selection(
      IntegrationCatalogGateway catalog, IntegrationConnectionSchemaReference... registered) {
    return new ConnectionIntegrationSelection(
        catalog, Arrays.stream(registered).map(TestIntegrationCatalogs::registered).toList());
  }

  private static IntegrationConnectionSchema registered(
      IntegrationConnectionSchemaReference reference) {
    var schema = mock(IntegrationConnectionSchema.class);
    when(schema.reference()).thenReturn(reference);
    return schema;
  }
}
