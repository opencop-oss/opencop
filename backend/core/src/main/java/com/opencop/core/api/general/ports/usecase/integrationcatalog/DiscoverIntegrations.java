// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.usecase.integrationcatalog;

import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationCatalogEntry;
import com.opencop.core.api.general.business.model.integrationcatalog.UnknownTransportProtocolException;
import java.util.List;

/** Reads the integration catalog as connection setup needs to see it. */
public interface DiscoverIntegrations {

  /**
   * Lists the connection schema versions a client may choose between, excluding every version of an
   * integration an operator deactivated.
   *
   * @param protocol a transport protocol name, matched ignoring case and surrounding whitespace,
   *     keeping only versions declaring it; {@code null} returns the whole catalog, including
   *     versions this runtime cannot serve
   * @return entries in a stable order, never null
   * @throws UnknownTransportProtocolException when a protocol was supplied but names none this
   *     build defines. A blank value counts as unknown.
   */
  List<IntegrationCatalogEntry> discover(String protocol);
}
