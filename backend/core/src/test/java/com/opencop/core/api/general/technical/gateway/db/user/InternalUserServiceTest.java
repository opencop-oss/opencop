// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.technical.gateway.scim.user.InternalUserEntity;
import com.opencop.core.api.general.technical.gateway.scim.user.InternalUserRepository;
import com.opencop.core.sdk.ports.gateway.idp.IdpException;
import com.opencop.core.sdk.ports.gateway.idp.IdpExceptionCodes;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InternalUserServiceTest {

  @Mock private InternalUserRepository repo;

  private InternalUserService service;

  @BeforeEach
  void setUp() {
    service = new InternalUserService(repo);
  }

  @Test
  void exists_delegatesToRepository() {
    var userId = UUID.randomUUID();
    when(repo.existsById(userId)).thenReturn(true);

    var exists = service.exists(userId);

    assertTrue(exists);
    verify(repo).existsById(userId);
  }

  @Test
  void get_byId_returnsMappedUser() throws IdpException {
    var entity = createEntity();
    when(repo.findById(entity.getId())).thenReturn(Optional.of(entity));

    var result = service.get(entity.getId());

    assertMatchesEntity(entity, result);
    verify(repo).findById(entity.getId());
  }

  @Test
  void get_byId_throwsNotFoundWhenMissing() {
    var userId = UUID.randomUUID();
    when(repo.findById(userId)).thenReturn(Optional.empty());

    var error = assertThrows(IdpException.class, () -> service.get(userId));

    assertEquals(IdpExceptionCodes.NOT_FOUND.code(), error.getCode());
    verify(repo).findById(userId);
  }

  @Test
  void get_byIdpIdentifiers_returnsMappedUser() throws IdpException {
    var entity = createEntity();
    when(repo.findByIdpIssuerAndIdpUserId(entity.getIdpIssuer(), entity.getIdpUserId()))
        .thenReturn(Optional.of(entity));

    var result = service.get(entity.getIdpUserId(), entity.getIdpIssuer());

    assertMatchesEntity(entity, result);
    verify(repo).findByIdpIssuerAndIdpUserId(entity.getIdpIssuer(), entity.getIdpUserId());
  }

  @Test
  void get_byIdpIdentifiers_throwsNotFoundWhenMissing() {
    var issuer = "issuer-a";
    var idpUserId = "idp-user-1";
    when(repo.findByIdpIssuerAndIdpUserId(issuer, idpUserId)).thenReturn(Optional.empty());

    var error = assertThrows(IdpException.class, () -> service.get(idpUserId, issuer));

    assertEquals(IdpExceptionCodes.NOT_FOUND.code(), error.getCode());
    verify(repo).findByIdpIssuerAndIdpUserId(issuer, idpUserId);
  }

  private static void assertMatchesEntity(
      InternalUserEntity entity,
      com.opencop.core.api.general.business.model.user.InternalUser user) {
    assertNotNull(user);
    assertEquals(entity.getId(), user.id());
    assertEquals(entity.getIdpUserId(), user.idpUserId());
    assertEquals(entity.getIdpUsername(), user.idpUsername());
    assertEquals(entity.getIdpIssuer(), user.issuer());
    assertEquals(entity.getUsername(), user.username());
    assertEquals(entity.getEmail(), user.email());
    assertEquals(entity.getFirstName(), user.firstName());
    assertEquals(entity.getLastName(), user.lastName());
    assertEquals(entity.getDisplayName(), user.displayName());
    assertEquals(entity.isActive(), user.active());
    assertEquals(entity.isAdministrator(), user.admin());
    assertEquals(entity.getSessionGeneration(), user.sessionGeneration());
    assertEquals(entity.getAccountAccessRevision(), user.accountAccessRevision());
    assertSame(entity.getCreatedAt(), user.createdAt());
    assertSame(entity.getUpdatedAt(), user.updatedAt());
    assertSame(entity.getDeletedAt(), user.deletedAt());
  }

  private static InternalUserEntity createEntity() {
    var entity = new InternalUserEntity();
    entity.setId(UUID.randomUUID());
    entity.setIdpUserId("idp-user-1");
    entity.setIdpUsername("user.one");
    entity.setIdpIssuer("issuer-a");
    entity.setUsername("user.one");
    entity.setEmail("user.one@example.com");
    entity.setFirstName("User");
    entity.setLastName("One");
    entity.setDisplayName("User One");
    entity.setActive(true);
    entity.setAdministrator(true);
    entity.setSessionGeneration(7);
    entity.setAccountAccessRevision(11);
    entity.setCreatedAt(Instant.parse("2024-01-02T03:04:05Z"));
    entity.setUpdatedAt(Instant.parse("2024-02-03T04:05:06Z"));
    entity.setDeletedAt(null);
    return entity;
  }
}
