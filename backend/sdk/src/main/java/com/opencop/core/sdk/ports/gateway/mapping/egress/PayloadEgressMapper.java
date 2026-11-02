// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.gateway.mapping.egress;

import java.util.UUID;
import org.geojson.Feature;

/**
 * An egress mapper: encodes a feature into the payload a writable connection sends to its peer.
 * Contributed through the egress-mapping capability; a connection writes only through the egress
 * mapper it selects.
 */
public interface PayloadEgressMapper {

  UUID id();

  String name();

  String description();

  int version();

  /** The payload the connection sends for the feature. */
  byte[] map(Feature feature) throws Exception;
}
