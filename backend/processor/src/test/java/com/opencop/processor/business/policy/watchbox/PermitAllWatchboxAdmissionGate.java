// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.policy.watchbox;

import com.opencop.processor.business.model.watchbox.WatchboxScopeKey;
import java.time.Instant;

public final class PermitAllWatchboxAdmissionGate implements WatchboxAdmissionGate {

  private static final AdmissionBatch BATCH = new AdmissionBatch() {};

  @Override
  public AdmissionBatch begin() {
    return BATCH;
  }

  @Override
  public Admission admit(
      AdmissionBatch batch, WatchboxScopeKey scope, String objectId, Instant observedAt) {
    return Admission.ADMITTED;
  }

  @Override
  public void confirm(AdmissionBatch batch) {}

  @Override
  public void release(AdmissionBatch batch) {}

  @Override
  public long cap() {
    return Long.MAX_VALUE;
  }
}
