// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.common.filter;

import com.opencop.core.sdk.business.model.Roles;
import com.opencop.core.sdk.business.model.account.CurrentAccountAccess;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.LinkedHashMap;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class CurrentAccountAuthenticationFactory {

  public Authentication create(
      OAuth2AuthenticationToken sessionAuthentication, CurrentAccountAccess access) {
    if (!(sessionAuthentication.getPrincipal() instanceof OpenCopPrincipal sessionPrincipal)) {
      throw new IllegalArgumentException("Session authentication has no OpenCop principal");
    }
    var currentPrincipal =
        sessionPrincipal.withRequestCurrentGlobalAdministrator(access.administrator());
    var currentAuthorities = new LinkedHashMap<String, GrantedAuthority>();
    sessionAuthentication.getAuthorities().stream()
        .filter(authority -> !Roles.ADMIN.equals(authority.getAuthority()))
        .forEach(authority -> currentAuthorities.putIfAbsent(authority.getAuthority(), authority));
    if (access.administrator()) {
      currentAuthorities.put(Roles.ADMIN, new SimpleGrantedAuthority(Roles.ADMIN));
    }
    var requestAuthentication =
        new OAuth2AuthenticationToken(
            currentPrincipal,
            currentAuthorities.values(),
            sessionAuthentication.getAuthorizedClientRegistrationId());
    requestAuthentication.setDetails(sessionAuthentication.getDetails());
    return requestAuthentication;
  }
}
