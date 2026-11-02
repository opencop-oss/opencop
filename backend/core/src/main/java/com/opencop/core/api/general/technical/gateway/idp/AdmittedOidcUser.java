// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.idp;

import com.opencop.core.api.general.business.model.auth.AdmittedOidcIdentity;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

public final class AdmittedOidcUser implements OidcUser {

  private final OidcUser delegate;
  private final AdmittedOidcIdentity admittedIdentity;

  public AdmittedOidcUser(OidcUser delegate, AdmittedOidcIdentity admittedIdentity) {
    this.delegate = Objects.requireNonNull(delegate, "delegate");
    this.admittedIdentity = Objects.requireNonNull(admittedIdentity, "admittedIdentity");
  }

  public AdmittedOidcIdentity admittedIdentity() {
    return admittedIdentity;
  }

  @Override
  public Map<String, Object> getClaims() {
    return delegate.getClaims();
  }

  @Override
  public OidcUserInfo getUserInfo() {
    return delegate.getUserInfo();
  }

  @Override
  public OidcIdToken getIdToken() {
    return delegate.getIdToken();
  }

  @Override
  public Map<String, Object> getAttributes() {
    return delegate.getAttributes();
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return delegate.getAuthorities();
  }

  @Override
  public String getName() {
    return delegate.getName();
  }
}
