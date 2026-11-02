// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.policy.connection.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.core.api.general.ports.usecase.connection.ConnectionExceptionCodes;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionManagementException;
import com.opencop.core.sdk.business.model.integration.connection.mapping.MapperDeclaration;
import com.opencop.core.sdk.business.model.profile.MapperProfile;
import com.opencop.core.sdk.business.model.profile.MapperQualifier;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IngressMapperSelectionPolicyTest {

  private static final UUID MAPPER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000a1");

  @Test
  void returnsAnUnarchivedIngressMapper() {
    var mapper = entry(MapperQualifier.INGRESS, false);

    assertThat(IngressMapperSelectionPolicy.requireSelectable(Optional.of(mapper)))
        .isSameAs(mapper);
  }

  @Test
  void rejectsAMapperTheCatalogDoesNotHold() {
    assertRejected(Optional.empty(), ConnectionExceptionCodes.MAPPER_NOT_FOUND);
  }

  /** An egress mapper encodes features; selected for ingress it would decode nothing. */
  @Test
  void rejectsAnEgressMapperAsIfItWereMissing() {
    assertRejected(
        Optional.of(entry(MapperQualifier.EGRESS, false)),
        ConnectionExceptionCodes.MAPPER_NOT_FOUND);
  }

  @Test
  void rejectsAnArchivedIngressMapper() {
    assertRejected(
        Optional.of(entry(MapperQualifier.INGRESS, true)),
        ConnectionExceptionCodes.MAPPER_ARCHIVED);
  }

  /** A mapper the requester chose freely is theirs to fix when it is unavailable. */
  @Test
  void blamesTheRequesterForAnUnavailableMapperTheyChoseFreely() {
    assertThatThrownBy(
            () ->
                IngressMapperSelectionPolicy.requireSelectable(
                    MapperDeclaration.openMapper(), Optional.empty()))
        .isInstanceOfSatisfying(
            ConnectionManagementException.class,
            exception ->
                assertThat(exception.getCode())
                    .isEqualTo(ConnectionExceptionCodes.MAPPER_NOT_FOUND.code()));
  }

  /** A mapper the integration imposes is the platform's to keep available. */
  @Test
  void blamesThePlatformForAnUnavailableMapperTheIntegrationImposes() {
    var imposed = MapperDeclaration.closedTo(Set.of(MAPPER_ID));

    for (var catalogEntry :
        List.of(
            Optional.<MapperProfile>empty(),
            Optional.of(entry(MapperQualifier.INGRESS, true)),
            Optional.of(entry(MapperQualifier.EGRESS, false)))) {
      assertThatThrownBy(
              () -> IngressMapperSelectionPolicy.requireSelectable(imposed, catalogEntry))
          .isInstanceOfSatisfying(
              ConnectionManagementException.class,
              exception ->
                  assertThat(exception.getCode())
                      .isEqualTo(ConnectionExceptionCodes.SYSTEM_MAPPER_UNAVAILABLE.code()));
    }
    var available = entry(MapperQualifier.INGRESS, false);
    assertThat(IngressMapperSelectionPolicy.requireSelectable(imposed, Optional.of(available)))
        .isSameAs(available);
  }

  private static void assertRejected(
      Optional<MapperProfile> catalogEntry, ConnectionExceptionCodes expected) {
    assertThatThrownBy(() -> IngressMapperSelectionPolicy.requireSelectable(catalogEntry))
        .isInstanceOfSatisfying(
            ConnectionManagementException.class,
            exception -> assertThat(exception.getCode()).isEqualTo(expected.code()));
  }

  private static MapperProfile entry(MapperQualifier qualifier, boolean archived) {
    return MapperProfile.builder()
        .withId(MAPPER_ID)
        .withName("Ingress")
        .withQualifier(qualifier)
        .withArchived(archived)
        .build();
  }
}
