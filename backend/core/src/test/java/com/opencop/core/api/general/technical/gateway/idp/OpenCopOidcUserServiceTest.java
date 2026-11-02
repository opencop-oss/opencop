// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.idp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.business.model.auth.AdmittedOidcIdentity;
import com.opencop.core.api.general.business.model.auth.OidcAdmissionCommand;
import com.opencop.core.api.general.business.model.auth.OidcAdmissionDeniedException;
import com.opencop.core.api.general.business.usecase.user.OidcAdmissionUsecase;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

class OpenCopOidcUserServiceTest {

  @SuppressWarnings("unchecked")
  private final OAuth2UserService<OidcUserRequest, OidcUser> delegate =
      mock(OAuth2UserService.class);

  private final OidcAdmissionCommandFactory commandFactory =
      mock(OidcAdmissionCommandFactory.class);
  private final OidcAdmissionUsecase admissionUsecase = mock(OidcAdmissionUsecase.class);
  private final OpenCopOidcUserService service =
      new OpenCopOidcUserService(delegate, commandFactory, admissionUsecase);

  @Test
  void delegatesStandardOidcLoadingThenAdmitsTheVerifiedResult() {
    var request = mock(OidcUserRequest.class);
    var loadedUser = mock(OidcUser.class);
    var command = mock(OidcAdmissionCommand.class);
    var identity = mock(AdmittedOidcIdentity.class);
    when(delegate.loadUser(request)).thenReturn(loadedUser);
    when(commandFactory.create(request, loadedUser)).thenReturn(command);
    when(admissionUsecase.admit(command)).thenReturn(identity);

    var result = service.loadUser(request);

    assertThat(result).isInstanceOf(AdmittedOidcUser.class);
    var admittedUser = (AdmittedOidcUser) result;
    assertThat(admittedUser.admittedIdentity()).isSameAs(identity);
    assertThat(admittedUser.getIdToken()).isSameAs(loadedUser.getIdToken());
    verify(delegate).loadUser(request);
    verify(commandFactory).create(request, loadedUser);
    verify(admissionUsecase).admit(command);
  }

  @Test
  void mapsExpectedAdmissionDenialToAStableOauthError() {
    var request = mock(OidcUserRequest.class);
    var loadedUser = mock(OidcUser.class);
    var command = mock(OidcAdmissionCommand.class);
    when(delegate.loadUser(request)).thenReturn(loadedUser);
    when(commandFactory.create(request, loadedUser)).thenReturn(command);
    when(admissionUsecase.admit(command)).thenThrow(new OidcAdmissionDeniedException());

    var error = assertThrows(OAuth2AuthenticationException.class, () -> service.loadUser(request));

    assertThat(error.getError().getErrorCode()).isEqualTo("opencop_access_denied");
    assertThat(error.getMessage()).doesNotContain("verified-subject", "profile@example.test");
  }

  @Test
  void mapsUnexpectedAdmissionFailuresToLoginFailedWithoutClaimValues() {
    var request = mock(OidcUserRequest.class);
    var cause = new IllegalStateException("sensitive-claim-value");
    when(delegate.loadUser(request)).thenThrow(cause);

    var error = assertThrows(OAuth2AuthenticationException.class, () -> service.loadUser(request));

    assertThat(error.getError().getErrorCode()).isEqualTo("opencop_login_failed");
    assertThat(error.getMessage()).doesNotContain("sensitive-claim-value");
    assertThat(error.getCause()).isSameAs(cause);
  }
}
