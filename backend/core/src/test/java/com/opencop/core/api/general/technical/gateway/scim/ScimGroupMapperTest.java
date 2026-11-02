// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.scim;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.opencop.core.sdk.technical.gateway.db.iam.group.GroupEntity;
import com.unboundid.scim2.common.types.GroupResource;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ScimGroupMapperTest {

  private static final UUID GROUP_ID = UUID.fromString("00000000-0000-0000-0000-000000000201");
  private static final String EXTERNAL_GROUP_ID = "external-group-a";
  private static final String DISPLAY_NAME = "Operators";
  private static final String REPLACEMENT_DISPLAY_NAME = "Field Operators";

  @Test
  void toScim_mapsIamGroupIdentityAndDisplayName() {
    var entity = new GroupEntity();
    entity.setId(GROUP_ID);
    entity.setIdpGroupId(EXTERNAL_GROUP_ID);
    entity.setName(DISPLAY_NAME);

    GroupResource result = ScimGroupMapper.toScim(entity);

    assertEquals(GROUP_ID.toString(), result.getId());
    assertEquals(EXTERNAL_GROUP_ID, result.getExternalId());
    assertEquals(DISPLAY_NAME, result.getDisplayName());
  }

  @Test
  void applyReplace_updatesIamGroupDisplayNameWithoutChangingIdentity() {
    var entity = new GroupEntity();
    entity.setId(GROUP_ID);
    entity.setIdpIssuer("issuer-a");
    entity.setIdpGroupId(EXTERNAL_GROUP_ID);
    entity.setName(DISPLAY_NAME);

    var replacement = new GroupResource();
    replacement.setDisplayName(REPLACEMENT_DISPLAY_NAME);

    ScimGroupMapper.applyReplace(replacement, entity);

    assertEquals(GROUP_ID, entity.getId());
    assertEquals("issuer-a", entity.getIdpIssuer());
    assertEquals(EXTERNAL_GROUP_ID, entity.getIdpGroupId());
    assertEquals(REPLACEMENT_DISPLAY_NAME, entity.getName());
  }
}
