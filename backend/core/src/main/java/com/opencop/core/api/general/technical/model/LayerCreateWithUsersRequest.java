// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.model;

import com.opencop.core.sdk.technical.gateway.db.iam.LayerAccessScope;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import java.util.Map;
import java.util.UUID;

/**
 * Core-specific layer creation request that extends the base request with optional initial users.
 * Backwards compatible: if 'users' is null/omitted, behaves like the original request.
 *
 * <p>Note: IAM service is planned to be merged into Core. Once that happens, this can be simplified
 * to handle layer creation and member assignment in a single transaction.
 */
public record LayerCreateWithUsersRequest(
    String name, Map<UUID, LayerRole> users, LayerAccessScope accessScope) {

  public LayerCreateWithUsersRequest(String name, Map<UUID, LayerRole> users) {
    this(name, users, LayerAccessScope.PRIVATE);
  }
}
