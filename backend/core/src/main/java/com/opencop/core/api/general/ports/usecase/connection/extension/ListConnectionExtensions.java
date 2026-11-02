// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.usecase.connection.extension;

import java.util.List;
import java.util.UUID;

/** Lists the extensions of one connection, with the operations the caller may invoke on it. */
public interface ListConnectionExtensions {

  List<ConnectionExtensionSummary> list(UUID sourceId, UUID userId, boolean isGlobalAdmin);
}
