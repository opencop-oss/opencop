// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.usecase.connection.restart;

import java.util.UUID;

/** Orchestrates a socket restart by advancing durable restart intent and nudging Connect. */
public interface RestartConnection {

  /**
   * Requests a restart for an enabled socket source.
   *
   * <p>Unlike stop/start which persist user intent via the enabled flag, restart leaves desired
   * state unchanged. Connect rereads Postgres desired ownership before applying the bounce.
   *
   * @param sourceId the data source to restart
   * @param userId the user requesting the restart
   */
  void restart(UUID sourceId, UUID userId);
}
