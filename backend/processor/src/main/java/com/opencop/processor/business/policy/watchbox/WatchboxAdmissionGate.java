// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.policy.watchbox;

import com.opencop.processor.business.model.watchbox.WatchboxScopeKey;
import java.time.Instant;

public interface WatchboxAdmissionGate {

  AdmissionBatch begin();

  Admission admit(
      AdmissionBatch batch, WatchboxScopeKey scope, String objectId, Instant observedAt);

  void confirm(AdmissionBatch batch);

  void release(AdmissionBatch batch);

  long cap();

  interface AdmissionBatch {}

  enum Admission {
    ADMITTED,
    FROZEN,
    TRIPPED
  }
}
