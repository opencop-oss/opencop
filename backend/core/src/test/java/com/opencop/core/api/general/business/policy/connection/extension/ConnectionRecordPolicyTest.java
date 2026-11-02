// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.policy.connection.extension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.core.api.general.business.model.connection.extension.RecordLimits;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionExceptionCodes;
import com.opencop.core.sdk.business.model.integration.connection.extension.ConnectionRecord;
import com.opencop.core.sdk.business.model.integration.connection.extension.ExpectedRevision;
import com.opencop.core.sdk.ports.integration.connection.extension.ConnectionExtensionException;
import com.opencop.core.sdk.ports.integration.connection.extension.ExtensionFailure;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class ConnectionRecordPolicyTest {

  private static final RecordLimits LIMITS = new RecordLimits(100, 3);
  private static final Optional<ConnectionRecord> MISSING = Optional.empty();
  private static final Optional<ConnectionRecord> AT_REVISION_2 = Optional.of(record(2));

  @Test
  void creatingStartsAtRevisionOne() throws Exception {
    assertThat(ConnectionRecordPolicy.nextRevision(MISSING, ExpectedRevision.absent()))
        .isEqualTo(1);
    assertThat(ConnectionRecordPolicy.nextRevision(MISSING, ExpectedRevision.any())).isEqualTo(1);
  }

  @Test
  void replacingAdvancesTheRevisionByOne() throws Exception {
    assertThat(ConnectionRecordPolicy.nextRevision(AT_REVISION_2, ExpectedRevision.of(2)))
        .isEqualTo(3);
    assertThat(ConnectionRecordPolicy.nextRevision(AT_REVISION_2, ExpectedRevision.any()))
        .isEqualTo(3);
  }

  @Test
  void writingAgainstAnotherRevisionConflicts() {
    assertRevisionConflict(
        () -> ConnectionRecordPolicy.nextRevision(AT_REVISION_2, ExpectedRevision.absent()));
    assertRevisionConflict(
        () -> ConnectionRecordPolicy.nextRevision(AT_REVISION_2, ExpectedRevision.of(1)));
    assertRevisionConflict(
        () -> ConnectionRecordPolicy.nextRevision(MISSING, ExpectedRevision.of(1)));
  }

  @Test
  void deletingRequiresTheExpectedRevision() throws Exception {
    assertThat(ConnectionRecordPolicy.isDeletable(AT_REVISION_2, ExpectedRevision.of(2))).isTrue();
    assertThat(ConnectionRecordPolicy.isDeletable(AT_REVISION_2, ExpectedRevision.any())).isTrue();
    assertThat(ConnectionRecordPolicy.isDeletable(MISSING, ExpectedRevision.any())).isFalse();
    assertRevisionConflict(
        () -> ConnectionRecordPolicy.isDeletable(AT_REVISION_2, ExpectedRevision.of(3)));
    assertRevisionConflict(
        () -> ConnectionRecordPolicy.isDeletable(MISSING, ExpectedRevision.of(1)));
  }

  @Test
  void deletingWhatMustBeAbsentIsAnExtensionBug() {
    assertThatThrownBy(
            () -> ConnectionRecordPolicy.isDeletable(AT_REVISION_2, ExpectedRevision.absent()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aDocumentWithinTheSizeLimitIsAccepted() {
    assertThatCode(() -> ConnectionRecordPolicy.assertWithinLimits(100, false, 3, LIMITS))
        .doesNotThrowAnyException();
  }

  @Test
  void aDocumentOverTheSizeLimitIsRefused() {
    assertLimit(() -> ConnectionRecordPolicy.assertWithinLimits(101, false, 0, LIMITS));
  }

  @Test
  void aNewRecordBeyondTheCountLimitIsRefusedButReplacingOneIsNot() {
    assertLimit(() -> ConnectionRecordPolicy.assertWithinLimits(10, true, 3, LIMITS));
    assertThatCode(() -> ConnectionRecordPolicy.assertWithinLimits(10, true, 2, LIMITS))
        .doesNotThrowAnyException();
    assertThatCode(() -> ConnectionRecordPolicy.assertWithinLimits(10, false, 3, LIMITS))
        .doesNotThrowAnyException();
  }

  private static void assertRevisionConflict(Executable call) {
    assertConflict(call, ConnectionExceptionCodes.CONNECTION_RECORD_REVISION_CONFLICT);
  }

  private static void assertLimit(Executable call) {
    assertConflict(call, ConnectionExceptionCodes.CONNECTION_RECORD_LIMIT);
  }

  private static void assertConflict(Executable call, ConnectionExceptionCodes code) {
    assertThatThrownBy(call::execute)
        .isInstanceOfSatisfying(
            ConnectionExtensionException.class,
            failure -> {
              assertThat(failure.failure()).isEqualTo(ExtensionFailure.CONFLICT);
              assertThat(failure.code()).contains(code.code());
            });
  }

  private static ConnectionRecord record(long revision) {
    var at = Instant.parse("2026-10-13T00:00:00Z");
    return new ConnectionRecord("subscriptions", "alpha", Map.of(), revision, at, null, at, null);
  }
}
