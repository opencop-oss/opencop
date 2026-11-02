// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.idp;

import com.opencop.core.api.general.business.model.auth.OidcAdmissionCommand;
import com.opencop.core.api.general.business.model.auth.OidcProfile;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

public final class OidcAdmissionCommandFactory {

  private static final String INVALID_CLAIM = "invalid_oidc_claim";
  private static final String INVALID_AUDIENCE = "invalid_oidc_audience";

  private final String userIdClaim;
  private final String expectedAudience;

  public OidcAdmissionCommandFactory(String userIdClaim, String expectedAudience) {
    this.userIdClaim = requireText(userIdClaim, "configured user ID claim");
    this.expectedAudience = Objects.requireNonNull(expectedAudience, "expectedAudience");
  }

  public OidcAdmissionCommand create(OidcUserRequest request, OidcUser loadedUser) {
    Objects.requireNonNull(request, "request");
    Objects.requireNonNull(loadedUser, "loadedUser");

    var idToken = request.getIdToken();
    var issuer = idToken.getIssuer();
    if (issuer == null) {
      throw invalidClaim("iss");
    }
    var subject = requiredText(idToken.getSubject(), "sub");
    var idpUserId =
        "sub".equals(userIdClaim) ? subject : requiredClaim(idToken.getClaims(), userIdClaim);
    var audiences =
        idToken.getAudience() == null ? List.<String>of() : List.copyOf(idToken.getAudience());
    if (!expectedAudience.isBlank() && !audiences.contains(expectedAudience)) {
      throw invalidAudience();
    }
    var attributes = loadedUser.getAttributes();

    return new OidcAdmissionCommand(
        issuer.toExternalForm(),
        subject,
        idpUserId,
        audiences,
        stringCollection(attributes, "groups"),
        stringCollection(attributes, "roles"),
        new OidcProfile(
            optionalString(attributes, "preferred_username"),
            optionalString(attributes, "name"),
            optionalString(attributes, "given_name"),
            optionalString(attributes, "family_name"),
            optionalString(attributes, "email"),
            optionalBoolean(attributes, "email_verified")));
  }

  private static List<String> stringCollection(Map<String, Object> attributes, String claimName) {
    var value = attributes.get(claimName);
    if (value == null) {
      return List.of();
    }
    if (!(value instanceof Collection<?> collection)) {
      throw invalidClaim(claimName);
    }
    var values = new LinkedHashSet<String>();
    for (var element : collection) {
      if (!(element instanceof String text) || text.isBlank()) {
        throw invalidClaim(claimName);
      }
      values.add(text);
    }
    return List.copyOf(values);
  }

  private static String optionalString(Map<String, Object> attributes, String claimName) {
    var value = attributes.get(claimName);
    return value instanceof String text ? text : null;
  }

  private static Boolean optionalBoolean(Map<String, Object> attributes, String claimName) {
    var value = attributes.get(claimName);
    return value instanceof Boolean flag ? flag : null;
  }

  private static String requiredClaim(Map<String, Object> claims, String claimName) {
    var value = claims.get(claimName);
    if (!(value instanceof String text)) {
      throw invalidClaim(claimName);
    }
    return requiredText(text, claimName);
  }

  private static String requiredText(String value, String claimName) {
    if (value == null || value.isBlank()) {
      throw invalidClaim(claimName);
    }
    return value;
  }

  private static String requireText(String value, String field) {
    Objects.requireNonNull(value, field);
    if (value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value;
  }

  private static OAuth2AuthenticationException invalidClaim(String claimName) {
    return new OAuth2AuthenticationException(
        new OAuth2Error(INVALID_CLAIM), "OIDC claim '" + claimName + "' has an invalid shape");
  }

  private static OAuth2AuthenticationException invalidAudience() {
    return new OAuth2AuthenticationException(
        new OAuth2Error(INVALID_AUDIENCE), "OIDC token audience is invalid");
  }
}
