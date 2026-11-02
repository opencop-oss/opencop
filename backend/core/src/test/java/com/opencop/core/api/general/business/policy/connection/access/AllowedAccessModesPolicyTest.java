// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.policy.connection.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.core.api.general.ports.usecase.connection.ConnectionExceptionCodes;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionManagementException;
import com.opencop.core.sdk.business.model.integration.TransportProtocol;
import com.opencop.core.sdk.business.model.integration.connection.mapping.EgressMapperRule;
import com.opencop.core.sdk.business.model.integration.connection.mapping.FramingRule;
import com.opencop.core.sdk.business.model.integration.connection.mapping.MapperDeclaration;
import com.opencop.core.sdk.business.model.integration.connection.mapping.MapperFramingDeclaration;
import com.opencop.core.sdk.model.connection.add.TransportKind;
import com.opencop.core.sdk.model.datasource.AccessMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class AllowedAccessModesPolicyTest {

  private static final MapperFramingDeclaration TCP_READ_ONLY =
      MapperFramingDeclaration.of(new FramingRule.AnyMapperCustomFraming());
  private static final MapperFramingDeclaration TCP_WRITABLE =
      TCP_READ_ONLY.withEgress(EgressMapperRule.openMapper());
  private static final MapperDeclaration MAPPER_ONLY = MapperDeclaration.openMapper();

  @Test
  void letsATcpDeclarationWithAnEgressRuleReadWriteOrBoth() {
    assertThat(AllowedAccessModesPolicy.allowedModes(TransportProtocol.TCP, TCP_WRITABLE))
        .containsExactly(AccessMode.READ, AccessMode.WRITE, AccessMode.READ_WRITE);
  }

  @Test
  void letsATcpDeclarationWithoutAnEgressRuleOnlyRead() {
    assertThat(AllowedAccessModesPolicy.allowedModes(TransportProtocol.TCP, TCP_READ_ONLY))
        .containsExactly(AccessMode.READ);
  }

  @ParameterizedTest
  @EnumSource(
      value = TransportProtocol.class,
      names = {"UDP", "RTSP", "KAFKA"})
  void letsEveryOtherTransportOnlyRead(TransportProtocol transport) {
    assertThat(AllowedAccessModesPolicy.allowedModes(transport, MAPPER_ONLY))
        .containsExactly(AccessMode.READ);
  }

  /** The transport caps what a declaration permits, whatever the declaration's shape. */
  @ParameterizedTest
  @EnumSource(
      value = TransportProtocol.class,
      names = {"UDP", "RTSP", "KAFKA"})
  void letsNoOtherTransportWriteEvenWithAnEgressRule(TransportProtocol transport) {
    assertThat(AllowedAccessModesPolicy.allowedModes(transport, TCP_WRITABLE))
        .containsExactly(AccessMode.READ);
  }

  @ParameterizedTest
  @EnumSource(AccessMode.class)
  void allowsEveryModeItDerives(AccessMode mode) {
    assertThatCode(
            () -> AllowedAccessModesPolicy.assertAllows(TransportProtocol.TCP, TCP_WRITABLE, mode))
        .doesNotThrowAnyException();
  }

  @ParameterizedTest
  @EnumSource(
      value = AccessMode.class,
      names = {"WRITE", "READ_WRITE"})
  void rejectsWritingThroughATcpDeclarationWithoutAnEgressRule(AccessMode mode) {
    assertThatThrownBy(
            () -> AllowedAccessModesPolicy.assertAllows(TransportProtocol.TCP, TCP_READ_ONLY, mode))
        .isInstanceOfSatisfying(
            ConnectionManagementException.class,
            exception ->
                assertThat(exception.getCode())
                    .isEqualTo(ConnectionExceptionCodes.UNSUPPORTED_ACCESS_MODE.code()));
  }

  @ParameterizedTest
  @EnumSource(AccessMode.class)
  void letsTcpCarryAnyModeBeforeTheDeclarationIsKnown(AccessMode mode) {
    assertThatCode(() -> AllowedAccessModesPolicy.assertTransportAllows(TransportKind.TCP, mode))
        .doesNotThrowAnyException();
  }

  @ParameterizedTest
  @EnumSource(
      value = TransportKind.class,
      names = {"UDP", "RTSP", "KAFKA"})
  void letsATransportThatOnlyReadsTakeReadAlone(TransportKind transport) {
    assertThatCode(() -> AllowedAccessModesPolicy.assertTransportAllows(transport, AccessMode.READ))
        .doesNotThrowAnyException();
    for (var mode : new AccessMode[] {AccessMode.WRITE, AccessMode.READ_WRITE}) {
      assertThatThrownBy(() -> AllowedAccessModesPolicy.assertTransportAllows(transport, mode))
          .as(mode.name())
          .isInstanceOfSatisfying(
              ConnectionManagementException.class,
              exception ->
                  assertThat(exception.getCode())
                      .isEqualTo(ConnectionExceptionCodes.UNSUPPORTED_ACCESS_MODE.code()));
    }
  }
}
