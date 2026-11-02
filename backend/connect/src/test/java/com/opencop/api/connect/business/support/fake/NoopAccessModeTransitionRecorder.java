// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.business.support.fake;

import com.opencop.api.connect.ports.gateway.observability.AccessModeTransitionRecorder;
import com.opencop.core.sdk.model.connection.add.TransportKind;
import com.opencop.core.sdk.model.datasource.AccessMode;
import org.jspecify.annotations.Nullable;

/** Test-only recorder for scenarios that do not inspect access-mode telemetry. */
public final class NoopAccessModeTransitionRecorder implements AccessModeTransitionRecorder {

  public static final NoopAccessModeTransitionRecorder INSTANCE =
      new NoopAccessModeTransitionRecorder();

  private NoopAccessModeTransitionRecorder() {}

  @Override
  public void record(
      TransportKind transport,
      @Nullable AccessMode previousAccess,
      AccessMode newAccess,
      Cause cause) {}

  @Override
  public void recordIncompatibility(TransportKind transport, AccessMode access) {}
}
