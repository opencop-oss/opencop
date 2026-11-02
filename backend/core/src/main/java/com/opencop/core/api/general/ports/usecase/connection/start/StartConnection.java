// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.usecase.connection.start;

import com.opencop.core.api.general.business.model.Caller;
import java.util.UUID;

public interface StartConnection {

  void start(UUID sourceId, Caller actor);
}
