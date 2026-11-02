// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.connection.access;

import com.opencop.core.sdk.model.datasource.AccessMode;
import java.util.Optional;
import java.util.UUID;

/**
 * What a connection does with its data, and the mappers that lets it do it: reading needs an
 * ingress mapper, writing an egress mapper. Each access holds exactly the mappers its mode needs.
 */
public sealed interface ConnectionAccess permits ReadAccess, WriteAccess, ReadWriteAccess {

  AccessMode mode();

  /** The mapper decoding what the connection reads, when it reads. */
  Optional<UUID> ingressMapperId();

  /** The mapper encoding what the connection writes, when it writes. */
  Optional<UUID> egressMapperId();
}
