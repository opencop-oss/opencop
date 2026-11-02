// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.idp;

import com.opencop.core.api.general.business.model.auth.OidcAdmissionDeniedException;
import com.opencop.core.api.general.business.usecase.user.OidcAdmissionUsecase;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

public class OpenCopOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

  private final OAuth2UserService<OidcUserRequest, OidcUser> delegate;
  private final OidcAdmissionCommandFactory commandFactory;
  private final OidcAdmissionUsecase admissionUsecase;

  public OpenCopOidcUserService(
      OAuth2UserService<OidcUserRequest, OidcUser> delegate,
      OidcAdmissionCommandFactory commandFactory,
      OidcAdmissionUsecase admissionUsecase) {
    this.delegate = delegate;
    this.commandFactory = commandFactory;
    this.admissionUsecase = admissionUsecase;
  }

  @Override
  public OidcUser loadUser(OidcUserRequest request) throws OAuth2AuthenticationException {
    try {
      var loadedUser = delegate.loadUser(request);
      var command = commandFactory.create(request, loadedUser);
      return new AdmittedOidcUser(loadedUser, admissionUsecase.admit(command));
    } catch (OidcAdmissionDeniedException _) {
      throw oauthError("opencop_access_denied", "OpenCOP denied OIDC admission");
    } catch (OAuth2AuthenticationException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw oauthError("opencop_login_failed", "OpenCOP OIDC login failed", exception);
    }
  }

  private static OAuth2AuthenticationException oauthError(String code, String message) {
    return new OAuth2AuthenticationException(new OAuth2Error(code), message);
  }

  private static OAuth2AuthenticationException oauthError(
      String code, String message, RuntimeException cause) {
    return new OAuth2AuthenticationException(new OAuth2Error(code), message, cause);
  }
}
