// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.business.model.user.AccountAlreadyActiveException;
import com.opencop.core.api.general.business.model.user.ScimUserMutationConflictException;
import com.opencop.core.api.general.ports.technical.gateway.ScimUserMutationGateway;
import com.opencop.core.api.general.ports.technical.gateway.ScimUserMutationGateway.ActiveMutation;
import com.opencop.core.api.general.ports.technical.gateway.ScimUserMutationGateway.CreateMutation;
import com.opencop.core.api.general.ports.technical.gateway.ScimUserMutationGateway.PatchMutation;
import com.unboundid.scim2.common.messages.PatchOperation;
import com.unboundid.scim2.common.messages.PatchRequest;
import com.unboundid.scim2.common.types.UserResource;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScimUserMutationUsecaseTest {

  private static final UUID USER_ID = UUID.fromString("699a7747-74aa-43a5-b074-8fc01e9e9553");

  @Mock private ScimUserMutationGateway scimUserGateway;
  @Mock private AccountMutationUsecase accountMutationUsecase;
  @InjectMocks private ScimUserMutationUsecase usecase;

  @Test
  void create_whenGatewayCreatedRow_appliesCreatedAccountState() {
    var request = new UserResource();
    var response = response(false, "alpha.user");
    when(scimUserGateway.create(request))
        .thenReturn(new CreateMutation(USER_ID, "alpha@example.com", false, false, null, response));

    var created = usecase.create(request);

    assertSame(response, created);
    var ordered = inOrder(scimUserGateway, accountMutationUsecase);
    ordered.verify(scimUserGateway).create(request);
    ordered.verify(accountMutationUsecase).create(USER_ID, false);
  }

  @Test
  void create_whenGatewaySelectedInactiveRow_revalidatesThroughLockedReactivation() {
    var request = new UserResource();
    var response = response(true, "alpha.user");
    when(scimUserGateway.create(request))
        .thenReturn(
            new CreateMutation(
                USER_ID,
                "intended@example.com",
                true,
                true,
                "User with externalId already exists: ext-alpha",
                response));

    var created = usecase.create(request);

    assertSame(response, created);
    var ordered = inOrder(scimUserGateway, accountMutationUsecase);
    ordered.verify(scimUserGateway).create(request);
    ordered.verify(accountMutationUsecase).reactivate(USER_ID, "intended@example.com", true);
  }

  @Test
  void create_whenLockedReactivationBecameActive_translatesExistingScimConflictDetail() {
    var request = new UserResource();
    var conflictDetail = "User with externalId already exists: ext-alpha";
    when(scimUserGateway.create(request))
        .thenReturn(
            new CreateMutation(
                USER_ID,
                "intended@example.com",
                true,
                true,
                conflictDetail,
                response(true, "alpha.user")));
    doThrow(new AccountAlreadyActiveException(USER_ID))
        .when(accountMutationUsecase)
        .reactivate(USER_ID, "intended@example.com", true);

    var error =
        assertThrows(ScimUserMutationConflictException.class, () -> usecase.create(request));

    assertEquals(conflictDetail, error.getMessage());
    assertTrue(error.getCause() instanceof AccountAlreadyActiveException);
  }

  @Test
  void replace_persistsThroughGatewayThenAppliesRequestedAccountState() {
    var request = new UserResource();
    var response = response(true, "alpha.renamed");
    when(scimUserGateway.replace(USER_ID.toString(), request))
        .thenReturn(new ActiveMutation(USER_ID, true, response));

    var replaced = usecase.replace(USER_ID.toString(), request);

    assertSame(response, replaced);
    var ordered = inOrder(scimUserGateway, accountMutationUsecase);
    ordered.verify(scimUserGateway).replace(USER_ID.toString(), request);
    ordered.verify(accountMutationUsecase).setActive(USER_ID, true);
  }

  @Test
  void patch_appliesEveryRequestedAccountStateInRequestOrder() throws Exception {
    var request = new PatchRequest(PatchOperation.replace("active", false));
    var response = response(false, "alpha.user");
    when(scimUserGateway.patch(USER_ID, request))
        .thenReturn(new PatchMutation(USER_ID, List.of(false, true, false), response));

    var patched = usecase.patch(USER_ID, request);

    assertSame(response, patched);
    var ordered = inOrder(scimUserGateway, accountMutationUsecase);
    ordered.verify(scimUserGateway).patch(USER_ID, request);
    ordered.verify(accountMutationUsecase).setActive(USER_ID, false);
    ordered.verify(accountMutationUsecase).setActive(USER_ID, true);
    ordered.verify(accountMutationUsecase).setActive(USER_ID, false);
  }

  @Test
  void delete_requiresExistingScimUserThenSoftDeletesAccount() {
    when(scimUserGateway.requireExisting(USER_ID)).thenReturn(USER_ID);
    var startedAt = Instant.now();

    usecase.delete(USER_ID);

    var deletedAt = ArgumentCaptor.forClass(Instant.class);
    var ordered = inOrder(scimUserGateway, accountMutationUsecase);
    ordered.verify(scimUserGateway).requireExisting(USER_ID);
    ordered.verify(accountMutationUsecase).delete(eq(USER_ID), deletedAt.capture());
    assertFalse(deletedAt.getValue().isBefore(startedAt));
    assertTrue(deletedAt.getValue().compareTo(Instant.now()) <= 0);
  }

  private static UserResource response(boolean active, String userName) {
    var response = new UserResource();
    response.setId(USER_ID.toString());
    response.setUserName(userName);
    response.setActive(active);
    return response;
  }
}
