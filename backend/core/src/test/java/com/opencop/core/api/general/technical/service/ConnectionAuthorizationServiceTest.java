// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.api.iam.ports.gateway.repository.RepositoryException;
import com.opencop.core.api.iam.ports.gateway.repository.RepositoryExceptionCodes;
import com.opencop.core.api.iam.ports.gateway.repository.ServerUserRepository;
import com.opencop.core.api.iam.ports.usecase.access.me.UserSpecificManager;
import com.opencop.core.sdk.business.model.iam.access.SourceUser;
import com.opencop.core.sdk.model.iam.source.AccessVia;
import com.opencop.core.sdk.model.iam.source.SourceRole;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ConnectionAuthorizationServiceTest {

  private static final UUID SOURCE_ID = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
  private static final UUID OTHER_SOURCE_ID =
      UUID.fromString("00000000-0000-4000-8000-0000000000a2");
  private static final UUID USER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000b1");

  private final UserSpecificManager userManager = mock(UserSpecificManager.class);
  private final ServerUserRepository serverUsers = mock(ServerUserRepository.class);
  private final ConnectionAuthorizationService authorization =
      new ConnectionAuthorizationService(
          mock(DataSourceRepository.class),
          serverUsers,
          new SourceRelationshipAuthorizationService(userManager));

  @Test
  void aGlobalAdminCanReadAnyConnection() {
    assertThatCode(() -> authorization.assertCanRead(SOURCE_ID, USER_ID, true))
        .doesNotThrowAnyException();
    verifyNoInteractions(userManager);
  }

  @Test
  void anyEffectiveRoleLetsAUserReadTheConnection() {
    when(userManager.getUserServers(USER_ID))
        .thenReturn(List.of(new SourceRole(SOURCE_ID, ServerRole.VIEWER, AccessVia.INDIRECT)));

    assertThatCode(() -> authorization.assertCanRead(SOURCE_ID, USER_ID, false))
        .doesNotThrowAnyException();
  }

  @Test
  void aConnectionTheUserCannotSeeIsReportedAsMissing() {
    when(userManager.getUserServers(USER_ID))
        .thenReturn(List.of(new SourceRole(OTHER_SOURCE_ID, ServerRole.OWNER, AccessVia.DIRECT)));

    assertThatThrownBy(() -> authorization.assertCanRead(SOURCE_ID, USER_ID, false))
        .isInstanceOfSatisfying(
            GlobalOpenCopException.class,
            exception -> assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND));
  }

  @Test
  void aDirectOwnerOrGlobalAdminAdministersTheConnection() throws Exception {
    when(serverUsers.get(SOURCE_ID, USER_ID)).thenReturn(Set.of(sourceUser(ServerRole.OWNER)));

    assertThat(authorization.isDirectOwnerOrAdmin(SOURCE_ID, USER_ID, false)).isTrue();
    assertThat(authorization.isDirectOwnerOrAdmin(OTHER_SOURCE_ID, USER_ID, true)).isTrue();
  }

  @Test
  void aViewerOrAUserWithoutDirectAccessDoesNotAdministerTheConnection() throws Exception {
    when(serverUsers.get(SOURCE_ID, USER_ID)).thenReturn(Set.of(sourceUser(ServerRole.VIEWER)));
    when(serverUsers.get(OTHER_SOURCE_ID, USER_ID))
        .thenThrow(new RepositoryException(RepositoryExceptionCodes.DOES_NOT_EXIST));

    assertThat(authorization.isDirectOwnerOrAdmin(SOURCE_ID, USER_ID, false)).isFalse();
    assertThat(authorization.isDirectOwnerOrAdmin(OTHER_SOURCE_ID, USER_ID, false)).isFalse();
  }

  private static SourceUser sourceUser(ServerRole role) {
    var user = new SourceUser();
    user.setRole(role);
    return user;
  }
}
