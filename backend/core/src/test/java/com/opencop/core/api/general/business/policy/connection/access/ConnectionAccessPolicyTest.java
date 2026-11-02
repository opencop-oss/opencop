// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.policy.connection.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.core.api.general.business.model.connection.access.ReadAccess;
import com.opencop.core.api.general.business.model.connection.access.ReadWriteAccess;
import com.opencop.core.api.general.business.model.connection.access.WriteAccess;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionManagementException;
import com.opencop.core.sdk.model.datasource.AccessMode;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ConnectionAccessPolicyTest {

  private static final UUID INGRESS = UUID.fromString("0b7cf0a4-3d7e-4a55-9f39-2c3c1f0c9d01");
  private static final UUID EGRESS = UUID.fromString("5f0f6d1e-8e4b-4c1f-a0a7-7a9c2c8b1e02");

  @Test
  void readingNeedsOnlyAnIngressMapper() {
    assertThat(ConnectionAccessPolicy.resolveSelection(AccessMode.READ, INGRESS, null))
        .isEqualTo(new ReadAccess(INGRESS));
  }

  @Test
  void writingNeedsOnlyAnEgressMapper() {
    assertThat(ConnectionAccessPolicy.resolveSelection(AccessMode.WRITE, null, EGRESS))
        .isEqualTo(new WriteAccess(EGRESS));
  }

  @Test
  void readingAndWritingNeedsBothMappers() {
    assertThat(ConnectionAccessPolicy.resolveSelection(AccessMode.READ_WRITE, INGRESS, EGRESS))
        .isEqualTo(new ReadWriteAccess(INGRESS, EGRESS));
  }

  @ParameterizedTest
  @EnumSource(
      value = AccessMode.class,
      names = {"READ", "READ_WRITE"})
  void aReadingModeWithoutAnIngressMapperIsRejected(AccessMode mode) {
    var egress = mode.canWrite() ? EGRESS : null;
    assertReason(
        "INGRESS_MAPPER_REQUIRED",
        () -> ConnectionAccessPolicy.resolveSelection(mode, null, egress));
  }

  @ParameterizedTest
  @EnumSource(
      value = AccessMode.class,
      names = {"WRITE", "READ_WRITE"})
  void aWritingModeWithoutAnEgressMapperIsRejected(AccessMode mode) {
    var ingress = mode.canRead() ? INGRESS : null;
    assertReason(
        "EGRESS_MAPPER_REQUIRED",
        () -> ConnectionAccessPolicy.resolveSelection(mode, ingress, null));
  }

  /** A creation that sent a mapper its mode never uses expected it to matter, so it is refused. */
  @Test
  void aSelectionRejectsAMapperItsModeDoesNotUse() {
    assertThat(ConnectionAccessPolicy.resolveSelection(AccessMode.READ_WRITE, INGRESS, EGRESS))
        .isEqualTo(new ReadWriteAccess(INGRESS, EGRESS));
    assertReason(
        "EGRESS_MAPPER_NOT_ACCEPTED",
        () -> ConnectionAccessPolicy.resolveSelection(AccessMode.READ, INGRESS, EGRESS));
    assertReason(
        "INGRESS_MAPPER_NOT_ACCEPTED",
        () -> ConnectionAccessPolicy.resolveSelection(AccessMode.WRITE, INGRESS, EGRESS));
  }

  private static void assertReason(String code, ThrowingCallable call) {
    assertThatThrownBy(call)
        .isInstanceOfSatisfying(
            ConnectionManagementException.class,
            exception -> assertThat(exception.getCode()).isEqualTo(code));
  }
}
