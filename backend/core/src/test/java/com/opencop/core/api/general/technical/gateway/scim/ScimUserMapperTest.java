// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.scim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opencop.core.api.general.technical.gateway.scim.user.InternalUserEntity;
import com.unboundid.scim2.common.messages.PatchOperation;
import com.unboundid.scim2.common.types.Email;
import com.unboundid.scim2.common.types.UserResource;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ScimUserMapperTest {

  @Test
  void applyCreate_derivesUsernameFromEmailLocalPart() {
    var user = new UserResource();
    user.setExternalId("ext-alpha");
    user.setUserName("alpha.user");
    user.setDisplayName("Alpha User");
    user.setEmails(List.of(new Email().setValue("alpha@example.com").setPrimary(true)));

    var entity = new InternalUserEntity();

    ScimUserMapper.applyCreate(user, entity);

    assertEquals("alpha", entity.getUsername());
  }

  @Test
  void applyCreate_fallsBackToDisplayNameWhenEmailMissing() {
    var user = new UserResource();
    user.setExternalId("ext-alpha");
    user.setUserName("alpha.user");
    user.setDisplayName("Alpha User");

    var entity = new InternalUserEntity();

    ScimUserMapper.applyCreate(user, entity);

    assertEquals("Alpha User", entity.getUsername());
  }

  @Test
  void applyCreate_trimsBlankEmailAndFallsBackToDisplayNameLocalPart() {
    var user = new UserResource();
    user.setExternalId("ext-alpha");
    user.setUserName("alpha.user");
    user.setDisplayName("display@example.com");
    user.setEmails(List.of(new Email().setValue("   ").setPrimary(true)));

    var entity = new InternalUserEntity();

    ScimUserMapper.applyCreate(user, entity);

    assertEquals("display", entity.getUsername());
  }

  @Test
  void applyCreate_fallsBackToScimUsernameWhenEmailAndDisplayNameMissing() {
    var user = new UserResource();
    user.setExternalId("ext-alpha");
    user.setUserName("alpha.user");
    user.setDisplayName("   ");

    var entity = new InternalUserEntity();

    ScimUserMapper.applyCreate(user, entity);

    assertEquals("alpha.user", entity.getUsername());
  }

  @Test
  void applyCreate_usesPrimaryNonBlankEmailOverDisplayNameAndUsername() {
    var user = new UserResource();
    user.setExternalId("ext-alpha");
    user.setUserName("alpha.user");
    user.setDisplayName("display@example.com");
    user.setEmails(
        List.of(
            new Email().setValue("secondary@example.com").setPrimary(false),
            new Email().setValue("primary@example.com").setPrimary(true)));

    var entity = new InternalUserEntity();

    ScimUserMapper.applyCreate(user, entity);

    assertEquals("primary", entity.getUsername());
  }

  @Test
  void scimAttributeMapping_doesNotMutateAccountActiveState() {
    var createRequest = new UserResource();
    createRequest.setExternalId("ext-alpha");
    createRequest.setUserName("alpha.user");
    createRequest.setActive(true);
    var inactiveEntity = new InternalUserEntity();
    inactiveEntity.setActive(false);

    ScimUserMapper.applyCreate(createRequest, inactiveEntity);

    assertFalse(inactiveEntity.isActive());

    var replaceRequest = new UserResource();
    replaceRequest.setExternalId("ext-alpha");
    replaceRequest.setUserName("alpha.user");
    replaceRequest.setActive(false);
    var activeEntity = new InternalUserEntity();
    activeEntity.setActive(true);

    ScimUserMapper.applyReplace(replaceRequest, activeEntity);

    assertTrue(activeEntity.isActive());
  }

  @Test
  void toScim_detachesEveryResponseFieldFromEntity() {
    var entity = new InternalUserEntity();
    entity.setId(UUID.fromString("699a7747-74aa-43a5-b074-8fc01e9e9553"));
    entity.setIdpUserId("ext-alpha");
    entity.setIdpUsername("alpha.user");
    entity.setActive(false);
    entity.setFirstName("Alpha");
    entity.setLastName("User");
    entity.setEmail("alpha@example.com");

    var resource = ScimUserMapper.toScim(entity);
    entity.setIdpUsername("later.user");
    entity.setFirstName("Later");
    entity.setEmail("later@example.com");

    assertEquals("699a7747-74aa-43a5-b074-8fc01e9e9553", resource.getId());
    assertEquals("ext-alpha", resource.getExternalId());
    assertEquals("alpha.user", resource.getUserName());
    assertFalse(resource.getActive());
    assertEquals("Alpha", resource.getName().getGivenName());
    assertEquals("User", resource.getName().getFamilyName());
    assertEquals("alpha@example.com", resource.getEmails().getFirst().getValue());
    assertEquals("Alpha User", resource.getDisplayName());
  }

  @Test
  void patchOperations_doNotModifyUsername() throws Exception {
    var entity = new InternalUserEntity();
    entity.setUsername("custom-user");

    invokeApplyAddOrReplace(entity, PatchOperation.replace("displayName", "Renamed User"));
    invokeApplyAddOrReplace(entity, PatchOperation.replace("emails.value", "renamed@example.com"));

    assertEquals("custom-user", entity.getUsername());
  }

  private static void invokeApplyAddOrReplace(InternalUserEntity entity, PatchOperation operation)
      throws Exception {
    var service = new ScimUserService(null, "issuer");
    Method method =
        ScimUserService.class.getDeclaredMethod(
            "applyAddOrReplace", InternalUserEntity.class, PatchOperation.class, List.class);
    method.setAccessible(true);
    method.invoke(service, entity, operation, new ArrayList<Boolean>());
  }
}
