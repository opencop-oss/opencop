// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.usecase.connection.stop;

import com.opencop.core.api.general.business.model.Caller;
import java.util.UUID;

public interface StopConnection {

  void stop(UUID sourceId, Caller actor);
}
