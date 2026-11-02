// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.idp;

import com.opencop.core.sdk.business.model.Roles;
import java.util.LinkedHashMap;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.authentication.OAuth2LoginAuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

public class OidcAuthenticationResultConverter
    implements Converter<OAuth2LoginAuthenticationToken, OAuth2AuthenticationToken> {

  private final MinimalPrincipalFactory principalFactory;

  public OidcAuthenticationResultConverter(MinimalPrincipalFactory principalFactory) {
    this.principalFactory = principalFactory;
  }

  @Override
  public OAuth2AuthenticationToken convert(OAuth2LoginAuthenticationToken callback) {
    if (!(callback.getPrincipal() instanceof AdmittedOidcUser admittedUser)) {
      throw new OAuth2AuthenticationException(
          new OAuth2Error("opencop_admission_required"), "OIDC result was not admitted");
    }

    var authorities = new LinkedHashMap<String, GrantedAuthority>();
    callback.getAuthorities().stream()
        .filter(authority -> !Roles.ADMIN.equals(authority.getAuthority()))
        .forEach(
            authority ->
                authorities.putIfAbsent(
                    authority.getAuthority(),
                    new SimpleGrantedAuthority(authority.getAuthority())));
    return new OAuth2AuthenticationToken(
        principalFactory.create(admittedUser.admittedIdentity()),
        authorities.values(),
        callback.getClientRegistration().getRegistrationId());
  }
}
