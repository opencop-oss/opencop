// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.connection;

import com.opencop.core.api.general.business.model.Caller;
import com.opencop.core.api.general.business.model.connection.setup.KafkaSetup;
import java.util.Objects;
import java.util.UUID;

/**
 * A Kafka connection to create: who it is, and the validated setup it is created with.
 *
 * @param owner the user creating the connection, who becomes its owner
 */
public record NewKafkaConnection(UUID sourceId, String name, Caller owner, KafkaSetup setup) {

  public NewKafkaConnection {
    Objects.requireNonNull(sourceId, "sourceId");
    Objects.requireNonNull(owner, "owner");
    Objects.requireNonNull(setup, "setup");
  }
}
