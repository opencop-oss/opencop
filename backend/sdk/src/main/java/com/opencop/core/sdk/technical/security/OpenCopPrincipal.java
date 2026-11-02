// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.security;

import com.opencop.core.sdk.business.model.Roles;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;

/**
 * Minimal stable identity stored in the BFF session.
 *
 * <p>{@link #withRequestCurrentGlobalAdministrator(boolean)} creates a request-only view. Its
 * administrator override is transient and the returned principal must not be persisted.
 */
// AUTH_SESSION_LEGACY_PRINCIPAL_CLEANUP: remove these suppressions only after no deployed release
// can write the legacy representation and eight hours have elapsed since the last legacy writer
// left service. A rollback to a legacy writer resets that gate.
@SuppressWarnings({"java:S1133", "java:S5738"})
public final class OpenCopPrincipal implements OAuth2User, Serializable {

  @Serial private static final long serialVersionUID = 7158774449750379516L;

  private final UUID internalUserId;
  private final String issuer;
  private final String idpUserId;
  private final String subject;
  private final String preferredUsername;
  private final String fullName;
  private final String givenName;
  private final String familyName;
  private final String email;
  private final Boolean emailVerified;
  private final long sessionGeneration;
  private final Instant authenticatedAt;

  /**
   * AUTH_SESSION_LEGACY_PRINCIPAL_CLEANUP: remove after no deployed release can write the legacy
   * representation and eight hours have elapsed since the last legacy writer left service. A
   * rollback to a legacy writer resets that gate.
   *
   * @deprecated AUTH_SESSION_LEGACY_PRINCIPAL_CLEANUP: retained-session serialization field.
   */
  @Deprecated(forRemoval = true)
  @SuppressWarnings("java:S1948")
  private final OidcUser delegate;

  /**
   * AUTH_SESSION_LEGACY_PRINCIPAL_CLEANUP: remove after no deployed release can write the legacy
   * representation and eight hours have elapsed since the last legacy writer left service. A
   * rollback to a legacy writer resets that gate.
   *
   * @deprecated AUTH_SESSION_LEGACY_PRINCIPAL_CLEANUP: retained-session authority field.
   */
  @Deprecated(forRemoval = true)
  private final List<GrantedAuthority> additionalAuthorities;

  private final transient boolean hasCurrentGlobalAdministrator;
  private final transient boolean currentGlobalAdministrator;

  public OpenCopPrincipal(
      UUID internalUserId,
      String issuer,
      String idpUserId,
      String subject,
      Profile profile,
      long sessionGeneration,
      Instant authenticatedAt) {
    this(
        Objects.requireNonNull(internalUserId, "internalUserId"),
        requireText(issuer, "issuer"),
        requireText(idpUserId, "idpUserId"),
        requireText(subject, "subject"),
        Objects.requireNonNull(profile, "profile"),
        sessionGeneration,
        Objects.requireNonNull(authenticatedAt, "authenticatedAt"),
        null,
        null,
        false,
        false);
  }

  /**
   * AUTH_SESSION_LEGACY_PRINCIPAL_CLEANUP: retain only while sessions written by the legacy release
   * can exist. Remove after no deployed release can write the legacy representation and eight hours
   * have elapsed since the last legacy writer left service. A rollback to a legacy writer resets
   * that gate.
   *
   * @deprecated AUTH_SESSION_LEGACY_PRINCIPAL_CLEANUP: retained legacy writer constructor.
   */
  @Deprecated(forRemoval = true)
  public OpenCopPrincipal(UUID internalUserId, OidcUser delegate) {
    this(internalUserId, delegate, false, 0);
  }

  /**
   * AUTH_SESSION_LEGACY_PRINCIPAL_CLEANUP: retain only while sessions written by the legacy release
   * can exist. Remove after no deployed release can write the legacy representation and eight hours
   * have elapsed since the last legacy writer left service. A rollback to a legacy writer resets
   * that gate.
   *
   * @deprecated AUTH_SESSION_LEGACY_PRINCIPAL_CLEANUP: retained legacy writer constructor.
   */
  @Deprecated(forRemoval = true)
  public OpenCopPrincipal(UUID internalUserId, OidcUser delegate, boolean admin) {
    this(internalUserId, delegate, admin, 0);
  }

  /**
   * AUTH_SESSION_LEGACY_PRINCIPAL_CLEANUP: retain only while sessions written by the legacy release
   * can exist. Remove after no deployed release can write the legacy representation and eight hours
   * have elapsed since the last legacy writer left service. A rollback to a legacy writer resets
   * that gate.
   *
   * @deprecated AUTH_SESSION_LEGACY_PRINCIPAL_CLEANUP: retained legacy writer constructor.
   */
  @Deprecated(forRemoval = true)
  public OpenCopPrincipal(
      UUID internalUserId, OidcUser delegate, boolean admin, long sessionGeneration) {
    this(
        Objects.requireNonNull(internalUserId, "internalUserId"),
        null,
        null,
        null,
        Profile.empty(),
        sessionGeneration,
        null,
        Objects.requireNonNull(delegate, "delegate"),
        admin
            ? new ArrayList<>(List.of(new SimpleGrantedAuthority(Roles.ADMIN)))
            : new ArrayList<>(),
        false,
        false);
  }

  /**
   * AUTH_SESSION_LEGACY_PRINCIPAL_CLEANUP: this shared constructor carries the exact retained
   * delegate and authority fields through legacy construction and request-current copies. Remove
   * those parameters and the S107 suppression after no deployed release can write the legacy
   * representation and eight hours have elapsed since the last legacy writer left service. A
   * rollback to a legacy writer resets that gate.
   */
  @SuppressWarnings("java:S107")
  private OpenCopPrincipal(
      UUID internalUserId,
      String issuer,
      String idpUserId,
      String subject,
      Profile profile,
      long sessionGeneration,
      Instant authenticatedAt,
      OidcUser delegate,
      List<GrantedAuthority> additionalAuthorities,
      boolean hasCurrentGlobalAdministrator,
      boolean currentGlobalAdministrator) {
    this.internalUserId = internalUserId;
    this.issuer = issuer;
    this.idpUserId = idpUserId;
    this.subject = subject;
    this.preferredUsername = profile.preferredUsername();
    this.fullName = profile.fullName();
    this.givenName = profile.givenName();
    this.familyName = profile.familyName();
    this.email = profile.email();
    this.emailVerified = profile.emailVerified();
    this.sessionGeneration = sessionGeneration;
    this.authenticatedAt = authenticatedAt;
    this.delegate = delegate;
    this.additionalAuthorities = additionalAuthorities;
    this.hasCurrentGlobalAdministrator = hasCurrentGlobalAdministrator;
    this.currentGlobalAdministrator = currentGlobalAdministrator;
  }

  public UUID internalUserId() {
    return internalUserId;
  }

  public String issuer() {
    if (issuer != null) {
      return issuer;
    }
    // AUTH_SESSION_LEGACY_PRINCIPAL_CLEANUP: remove this retained-session fallback only after no
    // deployed release can write the legacy representation and eight hours have elapsed since the
    // last legacy writer left service. A rollback to a legacy writer resets that gate.
    return legacyIssuer();
  }

  public String idpUserId() {
    if (idpUserId != null) {
      return idpUserId;
    }
    // AUTH_SESSION_LEGACY_PRINCIPAL_CLEANUP: remove this retained-session fallback only after no
    // deployed release can write the legacy representation and eight hours have elapsed since the
    // last legacy writer left service. A rollback to a legacy writer resets that gate.
    return legacyIdpUserId();
  }

  public String subject() {
    if (subject != null) {
      return subject;
    }
    // AUTH_SESSION_LEGACY_PRINCIPAL_CLEANUP: remove this retained-session fallback only after no
    // deployed release can write the legacy representation and eight hours have elapsed since the
    // last legacy writer left service. A rollback to a legacy writer resets that gate.
    return legacyDelegate().getSubject();
  }

  public String preferredUsername() {
    // AUTH_SESSION_LEGACY_PRINCIPAL_CLEANUP: remove the retained-session operand only after no
    // deployed release can write the legacy representation and eight hours have elapsed since the
    // last legacy writer left service. A rollback to a legacy writer resets that gate.
    return preferredUsername != null || !isLegacyRepresentation()
        ? preferredUsername
        : legacyProfileValue(ProfileValue.PREFERRED);
  }

  public String fullName() {
    // AUTH_SESSION_LEGACY_PRINCIPAL_CLEANUP: remove the retained-session operand only after no
    // deployed release can write the legacy representation and eight hours have elapsed since the
    // last legacy writer left service. A rollback to a legacy writer resets that gate.
    return fullName != null || !isLegacyRepresentation()
        ? fullName
        : legacyProfileValue(ProfileValue.FULL_NAME);
  }

  public String givenName() {
    // AUTH_SESSION_LEGACY_PRINCIPAL_CLEANUP: remove the retained-session operand only after no
    // deployed release can write the legacy representation and eight hours have elapsed since the
    // last legacy writer left service. A rollback to a legacy writer resets that gate.
    return givenName != null || !isLegacyRepresentation()
        ? givenName
        : legacyProfileValue(ProfileValue.GIVEN_NAME);
  }

  public String familyName() {
    // AUTH_SESSION_LEGACY_PRINCIPAL_CLEANUP: remove the retained-session operand only after no
    // deployed release can write the legacy representation and eight hours have elapsed since the
    // last legacy writer left service. A rollback to a legacy writer resets that gate.
    return familyName != null || !isLegacyRepresentation()
        ? familyName
        : legacyProfileValue(ProfileValue.FAMILY_NAME);
  }

  public String email() {
    // AUTH_SESSION_LEGACY_PRINCIPAL_CLEANUP: remove the retained-session operand only after no
    // deployed release can write the legacy representation and eight hours have elapsed since the
    // last legacy writer left service. A rollback to a legacy writer resets that gate.
    return email != null || !isLegacyRepresentation()
        ? email
        : legacyProfileValue(ProfileValue.EMAIL);
  }

  public Boolean emailVerified() {
    // AUTH_SESSION_LEGACY_PRINCIPAL_CLEANUP: remove the retained-session operand only after no
    // deployed release can write the legacy representation and eight hours have elapsed since the
    // last legacy writer left service. A rollback to a legacy writer resets that gate.
    return emailVerified != null || !isLegacyRepresentation()
        ? emailVerified
        : legacyEmailVerified();
  }

  public long sessionGeneration() {
    return sessionGeneration;
  }

  public Instant authenticatedAt() {
    return authenticatedAt;
  }

  /** Returns a request-only view using the current global-administrator decision. */
  public OpenCopPrincipal withRequestCurrentGlobalAdministrator(boolean administrator) {
    return new OpenCopPrincipal(
        internalUserId,
        issuer,
        idpUserId,
        subject,
        new Profile(preferredUsername, fullName, givenName, familyName, email, emailVerified),
        sessionGeneration,
        authenticatedAt,
        delegate,
        additionalAuthorities,
        true,
        administrator);
  }

  public boolean isGlobalAdmin() {
    return getAuthorities().stream().anyMatch(auth -> Roles.ADMIN.equals(auth.getAuthority()));
  }

  @Override
  public Map<String, Object> getAttributes() {
    var attributes = new LinkedHashMap<String, Object>();
    putIfPresent(attributes, "iss", issuer());
    putIfPresent(attributes, "idp_user_id", idpUserId());
    putIfPresent(attributes, "sub", subject());
    putIfPresent(attributes, "preferred_username", preferredUsername());
    putIfPresent(attributes, "name", fullName());
    putIfPresent(attributes, "given_name", givenName());
    putIfPresent(attributes, "family_name", familyName());
    putIfPresent(attributes, "email", email());
    putIfPresent(attributes, "email_verified", emailVerified());
    return Collections.unmodifiableMap(attributes);
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    if (!isLegacyRepresentation()) {
      if (hasCurrentGlobalAdministrator && currentGlobalAdministrator) {
        return List.of(new SimpleGrantedAuthority(Roles.ADMIN));
      }
      return List.of();
    }

    // AUTH_SESSION_LEGACY_PRINCIPAL_CLEANUP: remove retained-session authority handling only after
    // no deployed release can write the legacy representation and eight hours have elapsed since
    // the last legacy writer left service. A rollback to a legacy writer resets that gate.
    var authorities = new ArrayList<GrantedAuthority>();
    legacyDelegate().getAuthorities().stream()
        .filter(
            authority ->
                !hasCurrentGlobalAdministrator || !Roles.ADMIN.equals(authority.getAuthority()))
        .forEach(authorities::add);
    if (!hasCurrentGlobalAdministrator) {
      authorities.addAll(additionalAuthorities == null ? List.of() : additionalAuthorities);
    } else if (currentGlobalAdministrator) {
      authorities.add(new SimpleGrantedAuthority(Roles.ADMIN));
    }
    return List.copyOf(authorities);
  }

  @Override
  public String getName() {
    return subject();
  }

  private static String requireText(String value, String field) {
    Objects.requireNonNull(value, field);
    if (value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value;
  }

  private static void putIfPresent(Map<String, Object> attributes, String name, Object value) {
    if (value != null) {
      attributes.put(name, value);
    }
  }

  /**
   * AUTH_SESSION_LEGACY_PRINCIPAL_CLEANUP: remove after no deployed release can write the legacy
   * representation and eight hours have elapsed since the last legacy writer left service. A
   * rollback to a legacy writer resets that gate.
   *
   * @deprecated AUTH_SESSION_LEGACY_PRINCIPAL_CLEANUP: retained representation discriminator.
   */
  @Deprecated(forRemoval = true)
  private boolean isLegacyRepresentation() {
    return delegate != null;
  }

  /**
   * AUTH_SESSION_LEGACY_PRINCIPAL_CLEANUP: remove after no deployed release can write the legacy
   * representation and eight hours have elapsed since the last legacy writer left service. A
   * rollback to a legacy writer resets that gate.
   *
   * @deprecated AUTH_SESSION_LEGACY_PRINCIPAL_CLEANUP: retained delegate accessor.
   */
  @Deprecated(forRemoval = true)
  private OidcUser legacyDelegate() {
    if (delegate == null) {
      throw new IllegalStateException("Minimal principal has no legacy OIDC delegate");
    }
    return delegate;
  }

  /**
   * AUTH_SESSION_LEGACY_PRINCIPAL_CLEANUP: remove after no deployed release can write the legacy
   * representation and eight hours have elapsed since the last legacy writer left service. A
   * rollback to a legacy writer resets that gate.
   *
   * @deprecated AUTH_SESSION_LEGACY_PRINCIPAL_CLEANUP: retained issuer reader.
   */
  @Deprecated(forRemoval = true)
  private String legacyIssuer() {
    var value = legacyDelegate().getClaims().get("iss");
    return value == null ? null : value.toString();
  }

  /**
   * AUTH_SESSION_LEGACY_PRINCIPAL_CLEANUP: this is the sole historical oid-then-sub compatibility
   * branch. Remove after no deployed release can write the legacy representation and eight hours
   * have elapsed since the last legacy writer left service. A rollback to a legacy writer resets
   * that gate.
   *
   * @deprecated AUTH_SESSION_LEGACY_PRINCIPAL_CLEANUP: sole oid-then-sub compatibility reader.
   */
  @Deprecated(forRemoval = true)
  private String legacyIdpUserId() {
    var oid = legacyDelegate().getClaims().get("oid");
    return oid == null ? legacyDelegate().getSubject() : oid.toString();
  }

  /**
   * AUTH_SESSION_LEGACY_PRINCIPAL_CLEANUP: remove after no deployed release can write the legacy
   * representation and eight hours have elapsed since the last legacy writer left service. A
   * rollback to a legacy writer resets that gate.
   *
   * @deprecated AUTH_SESSION_LEGACY_PRINCIPAL_CLEANUP: retained profile reader.
   */
  @Deprecated(forRemoval = true)
  private String legacyProfileValue(ProfileValue value) {
    return switch (value) {
      case PREFERRED -> legacyDelegate().getPreferredUsername();
      case FULL_NAME -> legacyDelegate().getFullName();
      case GIVEN_NAME -> legacyDelegate().getGivenName();
      case FAMILY_NAME -> legacyDelegate().getFamilyName();
      case EMAIL -> legacyDelegate().getEmail();
    };
  }

  /**
   * AUTH_SESSION_LEGACY_PRINCIPAL_CLEANUP: remove after no deployed release can write the legacy
   * representation and eight hours have elapsed since the last legacy writer left service. A
   * rollback to a legacy writer resets that gate.
   *
   * @deprecated AUTH_SESSION_LEGACY_PRINCIPAL_CLEANUP: retained email-verification reader.
   */
  @Deprecated(forRemoval = true)
  private Boolean legacyEmailVerified() {
    return legacyDelegate().getEmailVerified();
  }

  private enum ProfileValue {
    PREFERRED,
    FULL_NAME,
    GIVEN_NAME,
    FAMILY_NAME,
    EMAIL
  }

  public record Profile(
      String preferredUsername,
      String fullName,
      String givenName,
      String familyName,
      String email,
      Boolean emailVerified) {

    private static Profile empty() {
      return new Profile(null, null, null, null, null, null);
    }
  }
}
