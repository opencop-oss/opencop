// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.gateway.kafka;

import com.opencop.core.sdk.model.datasource.ByodSourceState;

public interface ByodSourceStatePublisher {

  void publish(ByodSourceState state);
}
