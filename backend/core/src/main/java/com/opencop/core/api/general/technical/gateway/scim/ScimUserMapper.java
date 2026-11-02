// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.scim;

import com.opencop.core.api.general.technical.gateway.scim.user.InternalUserEntity;
import com.unboundid.scim2.common.types.Email;
import com.unboundid.scim2.common.types.Name;
import com.unboundid.scim2.common.types.UserResource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

public final class ScimUserMapper {

  private ScimUserMapper() {}

  /** DB -> SCIM (library DTO) */
  public static UserResource toScim(InternalUserEntity entity) {
    UserResource user = new UserResource();

    user.setId(entity.getId().toString());
    // externalId = IDP id
    user.setExternalId(entity.getIdpUserId());
    user.setUserName(entity.getIdpUsername());
    user.setActive(entity.isActive());

    if (entity.getFirstName() != null || entity.getLastName() != null) {
      Name name = new Name();
      name.setGivenName(entity.getFirstName());
      name.setFamilyName(entity.getLastName());
      user.setName(name);
    }

    if (entity.getEmail() != null && !entity.getEmail().isBlank()) {
      Email email = new Email();
      email.setValue(entity.getEmail());
      email.setPrimary(true);

      var emails = new ArrayList<Email>(1);
      emails.add(email);
      user.setEmails(emails);
    }

    String displayName =
        (Optional.ofNullable(entity.getFirstName()).orElse("")
                + (entity.getLastName() != null ? (" " + entity.getLastName()) : ""))
            .trim();
    user.setDisplayName(displayName.isBlank() ? null : displayName);

    return user;
  }

  public static void applyCreate(UserResource scimUser, InternalUserEntity entity) {
    // IMPORTANT !!!! THE EXTERNAL ID MUST MATCH THE OID CLAIM IN THE TOKEN
    // eg IN AZURE WE MUST CONFIGURE externalId MAPPING TO objectId
    entity.setIdpUserId(scimUser.getExternalId());
    entity.setIdpUsername(scimUser.getUserName());

    Name name = scimUser.getName();
    entity.setFirstName(name != null ? name.getGivenName() : null);
    entity.setLastName(name != null ? name.getFamilyName() : null);

    entity.setEmail(extractBestEmail(scimUser));
    entity.setDisplayName(scimUser.getDisplayName());
    entity.setUsername(
        defaultUsername(entity.getEmail(), entity.getDisplayName(), scimUser.getUserName()));
  }

  public static void applyReplace(UserResource scimUser, InternalUserEntity entity) {
    entity.setIdpUserId(scimUser.getExternalId());

    entity.setIdpUsername(scimUser.getUserName());
    entity.setDisplayName(scimUser.getDisplayName());

    Name name = scimUser.getName();
    entity.setFirstName(name != null ? name.getGivenName() : null);
    entity.setLastName(name != null ? name.getFamilyName() : null);

    entity.setEmail(extractBestEmail(scimUser));
  }

  private static String extractBestEmail(UserResource scimUser) {
    if (scimUser.getEmails() == null || scimUser.getEmails().isEmpty()) {
      return null;
    }

    return scimUser.getEmails().stream()
        .filter(Objects::nonNull)
        .sorted(Comparator.comparing((Email e) -> Boolean.TRUE.equals(e.getPrimary())).reversed())
        .map(Email::getValue)
        .filter(v -> v != null && !v.isBlank())
        .findFirst()
        .orElse(null);
  }

  private static String defaultUsername(String email, String displayName, String fallback) {
    var candidate = firstNonBlank(email, displayName, fallback);
    if (candidate == null) {
      return null;
    }

    var atIndex = candidate.indexOf('@');
    return atIndex > 0 ? candidate.substring(0, atIndex) : candidate;
  }

  private static String firstNonBlank(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value.trim();
      }
    }
    return null;
  }
}
