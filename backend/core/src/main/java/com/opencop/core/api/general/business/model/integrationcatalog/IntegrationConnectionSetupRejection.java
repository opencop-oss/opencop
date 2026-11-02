// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.integrationcatalog;

/**
 * Why a client cannot be told how to set up a connection to an integration schema version.
 *
 * <p>Each reason is distinguishable so a client can tell a reference that was never in the catalog
 * from one this runtime cannot serve, and an administrator's deactivation from either. The messages
 * are deliberately non-specific: they name no integration, plugin or runtime detail.
 *
 * <p>Codes are shared with connection creation where the state is the same, so a client walking
 * from setup to creation sees one name per state. {@link #PROTOCOL_UNSUPPORTED} is the exception
 * and is documented on the constant.
 */
public enum IntegrationConnectionSetupRejection {

  /** No catalog row carries the requested identity and schema version. */
  UNKNOWN(IntegrationNotFoundException.CODE, "No such integration schema version"),

  /** An administrator has deactivated the integration. */
  INACTIVE("INTEGRATION_INACTIVE", "Integration is inactive"),

  /** The catalog knows the schema version, but this runtime registers no implementation for it. */
  UNAVAILABLE("INTEGRATION_UNAVAILABLE", "Integration is not available in this runtime"),

  /**
   * The integration is available but offers no protocol a connection can be set up over.
   *
   * <p>Deliberately distinct from the code connection creation raises when a request names a
   * protocol an integration does not declare. There, the caller asked to create something the
   * integration cannot do, which is a bad request. Here the request is well formed and the resource
   * exists; it is the integration's current shape that prevents describing it, which is the same
   * conflict the other reasons on this enum report.
   */
  PROTOCOL_UNSUPPORTED(
      "INTEGRATION_PROTOCOL_UNSUPPORTED", "Integration declares no connection setup");

  private final String code;
  private final String message;

  IntegrationConnectionSetupRejection(String code, String message) {
    this.code = code;
    this.message = message;
  }

  public String code() {
    return code;
  }

  public String message() {
    return message;
  }
}
