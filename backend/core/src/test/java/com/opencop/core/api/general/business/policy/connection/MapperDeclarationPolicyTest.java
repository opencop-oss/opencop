// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.policy.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.core.api.general.ports.usecase.connection.ConnectionExceptionCodes;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionManagementException;
import com.opencop.core.sdk.business.model.integration.connection.mapping.MapperDeclaration;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MapperDeclarationPolicyTest {

  private static final UUID LISTED = UUID.fromString("00000000-0000-4000-8000-0000000000a1");

  @Test
  void acceptsAnyMapperUnderAnOpenDeclaration() {
    assertThatNoException()
        .isThrownBy(
            () ->
                MapperDeclarationPolicy.assertAllows(
                    MapperDeclaration.openMapper(), UUID.randomUUID()));
  }

  @Test
  void acceptsOnlyAListedMapperUnderAClosedDeclaration() {
    var closed = MapperDeclaration.closedTo(Set.of(LISTED));

    assertThatNoException().isThrownBy(() -> MapperDeclarationPolicy.assertAllows(closed, LISTED));
    assertThatThrownBy(() -> MapperDeclarationPolicy.assertAllows(closed, UUID.randomUUID()))
        .isInstanceOfSatisfying(
            ConnectionManagementException.class,
            exception ->
                assertThat(exception.getCode())
                    .isEqualTo(ConnectionExceptionCodes.MAPPER_NOT_ALLOWED.code()));
  }
}
