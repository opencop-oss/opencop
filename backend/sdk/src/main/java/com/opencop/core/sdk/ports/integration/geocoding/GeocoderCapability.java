// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.integration.geocoding;

import com.opencop.core.sdk.business.model.integration.Integration;
import com.opencop.core.sdk.business.model.integration.geocoding.GeocoderDescriptor;
import java.util.UUID;

/**
 * Identity of a geocoder an integration contributes.
 *
 * <p>Holds no operation itself: a geocoder supports exactly the operation interfaces it implements,
 * such as {@link ForwardGeocoding}, and the registry rejects one that implements none.
 */
public interface GeocoderCapability {

  /** Returns the integration contributing this geocoder. */
  Integration integration();

  /**
   * Returns the stable geocoder identifier.
   *
   * <p>A contract value, not a display string: clients receive it from discovery and send it back
   * to select this geocoder, so changing it is a breaking change. Globally unique, which the
   * registry enforces.
   */
  UUID id();

  /**
   * Returns this geocoder's display metadata.
   *
   * <p>Purely declarative: resolvable without I/O, because callers resolve it to list geocoders
   * that are not being called.
   */
  GeocoderDescriptor descriptor();
}
