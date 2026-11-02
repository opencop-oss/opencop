// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.connection.configuration;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A change to what a running connection applies without reconnecting: its access mode with the
 * mappers it reads and writes with, which change together, and its integration settings overrides.
 * Absent fields leave the connection as it is; an access field sent as null is absent too. Once any
 * of the access fields is sent, the access mode is required and a mapper that is not sent is none,
 * exactly as on creation.
 *
 * @param access the raw access mode the request named, or null when it named none
 * @param mapperId the ingress mapper the connection reads with, or null for none
 * @param egressMapperId the egress mapper the connection writes with, or null for none
 * @param settings the overrides that replace the connection's whole document, as a parsed JSON
 *     object of plain values; absent when the request sent none, and an empty map when it clears
 *     them
 */
public record ConnectionConfigurationChange(
    String access, UUID mapperId, UUID egressMapperId, Optional<Map<String, ?>> settings) {

  public ConnectionConfigurationChange {
    Objects.requireNonNull(settings, "settings");
  }

  /** Whether the change names the connection's access at all. */
  public boolean changesAccess() {
    return access != null || mapperId != null || egressMapperId != null;
  }

  /** Whether the change replaces or clears the connection's settings overrides. */
  public boolean changesSettings() {
    return settings.isPresent();
  }
}
