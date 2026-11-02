// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.iam.group;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * The classification rule has to stay the same one the SCIM-managed guard applies, or a group could
 * be labelled locally-managed in a picker while the server refuses local mutations on it.
 */
class GroupManagementSourceTest {

  @Test
  void ofIdpIssuer_classifiesAnAbsentIssuerAsLocallyManaged() {
    assertEquals(GroupManagementSource.OPENCOP, GroupManagementSource.ofIdpIssuer(null));
  }

  @Test
  void ofIdpIssuer_classifiesAnyIssuerAsScimManaged() {
    assertEquals(
        GroupManagementSource.SCIM, GroupManagementSource.ofIdpIssuer("https://idp.example"));
    // Blank is not null. A row carrying an empty issuer is still IdP-owned as far as every
    // issuer-scoped query is concerned, so it must not read as locally-managed.
    assertEquals(GroupManagementSource.SCIM, GroupManagementSource.ofIdpIssuer(""));
  }
}
