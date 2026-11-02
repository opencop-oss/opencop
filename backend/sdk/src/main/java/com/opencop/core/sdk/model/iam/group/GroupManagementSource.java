// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.iam.group;

/**
 * Who manages a user group's lifecycle.
 *
 * <p>Since V23 a group name is unique only within its own scope — {@code uk_user_group_admin_name}
 * for locally-created groups, {@code uk_user_group_issuer_name} per SCIM issuer — so a local group
 * and a provisioned group may legally share a name. Assign pickers identify a group by name, which
 * leaves two candidates indistinguishable on an access-grant operation without this signal.
 *
 * <p>Deliberately coarse. It classifies management without disclosing the issuer string or the
 * external group id, which stay off the wire.
 */
public enum GroupManagementSource {
  /** Created and managed inside OpenCOP. Carries no {@code idpIssuer}. */
  OPENCOP,
  /** Provisioned over SCIM by the identity provider, and only mutable through it. */
  SCIM;

  /**
   * Classifies a group by whether it carries an IdP issuer, the same rule the SCIM-managed guard in
   * {@code AppGroupService} applies when refusing local mutations.
   */
  public static GroupManagementSource ofIdpIssuer(String idpIssuer) {
    return idpIssuer == null ? OPENCOP : SCIM;
  }
}
