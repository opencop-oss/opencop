// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.support.assertion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.opencop.core.sdk.model.connection.ConnectionInfo;
import com.opencop.core.sdk.model.connection.add.SecurityKind;
import com.opencop.core.sdk.model.connection.add.TransportKind;
import com.opencop.core.sdk.ports.gateway.socket.ConnectorState;
import java.util.Map;
import java.util.UUID;

public final class ConnectionAssertions {

  private ConnectionAssertions() {}

  public static void assertConnectionInfo(
      ConnectionInfo connection,
      UUID expectedId,
      String expectedName,
      ConnectorState expectedState,
      String expectedAssignedInstance) {
    assertEquals(expectedId, connection.id());
    assertEquals(expectedName, connection.name());
    assertEquals(TransportKind.TCP, connection.transportType());
    assertEquals(SecurityKind.NONE, connection.securityType());
    assertEquals(expectedState, connection.state());
    assertEquals(expectedAssignedInstance, connection.assignedInstance());
  }

  public static void assertCreatedConnectionInfo(
      ConnectionInfo connection,
      String expectedName,
      ConnectorState expectedState,
      String expectedAssignedInstance) {
    assertNotNull(connection.id());
    assertConnectionInfo(
        connection, connection.id(), expectedName, expectedState, expectedAssignedInstance);
  }

  public static void assertPersistedDataSource(
      Map<String, Object> row, String expectedProvider, boolean enabled, String assignedInstance) {
    assertEquals(expectedProvider, row.get("provider").toString());
    assertEquals(enabled, row.get("enabled"));
    assertEquals(assignedInstance, row.get("assigned_instance"));
  }
}
