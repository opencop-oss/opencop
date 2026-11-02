// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.user;

import com.opencop.core.api.general.business.model.user.UserPreference;
import com.opencop.core.api.general.business.model.user.UserPreferencePatch;
import com.opencop.core.api.general.business.policy.MapTilePolicy;
import com.opencop.core.api.general.business.usecase.map.MapTileAccessUsecase;
import com.opencop.core.api.general.ports.gateway.repository.UserPreferenceRepository;
import com.opencop.core.api.general.ports.usecase.media.RetrieveMedia;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Reading and changing the caller's own preferences.
 *
 * <p>The default tile is the one field here that carries an access decision. It is persisted as a
 * raw id, and access to that tile can be revoked, narrowed or disabled long after it was chosen, so
 * the rule is applied twice: on the way in, so a stored preference is never a tile the caller
 * cannot use, and again on every response that expands it.
 */
@Service
public class UserPreferenceUsecase {

  private static final Logger log = LoggerFactory.getLogger(UserPreferenceUsecase.class);
  private static final Set<String> VALID_TIMEZONES = ZoneId.getAvailableZoneIds();

  private final UserPreferenceRepository repository;
  private final RetrieveMedia retrieveMedia;
  private final MapTileAccessUsecase tileAccess;

  public UserPreferenceUsecase(
      UserPreferenceRepository repository,
      RetrieveMedia retrieveMedia,
      MapTileAccessUsecase tileAccess) {
    this.repository = repository;
    this.retrieveMedia = retrieveMedia;
    this.tileAccess = tileAccess;
  }

  public UserPreference get(OpenCopPrincipal principal) {
    return forResponse(repository.findByUserId(principal.internalUserId()), principal);
  }

  public UserPreference patch(UserPreferencePatch body, OpenCopPrincipal principal) {
    var validated = validate(body);
    assertDefaultTileUsable(validated, principal);
    try {
      return forResponse(
          repository.patch(principal.internalUserId(), validated.withoutAvatar()), principal);
    } catch (PersistenceException e) {
      throw new GlobalOpenCopException(e.getMessage(), HttpStatus.BAD_REQUEST);
    }
  }

  /**
   * Storing an arbitrary id would let anyone nominate a tile they cannot see and have the response
   * hydrate its token later. Rejecting on the way in keeps the stored preference honest.
   */
  private void assertDefaultTileUsable(UserPreferencePatch body, OpenCopPrincipal principal) {
    body.defaultTileId()
        .ifPresent(
            tileId -> {
              if (tileId != null) {
                MapTilePolicy.assertMayBeDefaultTile(tileAccess.canUse(tileId, principal));
              }
            });
  }

  /**
   * Prepares a stored preference for any response that carries one: the avatar is expanded, and a
   * default tile the caller can no longer use is dropped rather than handed back.
   */
  public UserPreference forResponse(UserPreference pref, OpenCopPrincipal principal) {
    return enrichAvatar(withoutUnusableDefaultTile(pref, principal), principal.internalUserId());
  }

  private UserPreference withoutUnusableDefaultTile(
      UserPreference pref, OpenCopPrincipal principal) {
    var tile = pref.defaultTile();
    if (tile == null || tileAccess.canUse(tile.getId(), principal)) {
      return pref;
    }
    return pref.withoutDefaultTile();
  }

  /** An unreadable avatar must not fail the whole request; the preference is still useful. */
  private UserPreference enrichAvatar(UserPreference pref, UUID userId) {
    if (pref.avatarMediaId() == null) {
      return pref;
    }
    try {
      return pref.withAvatar(retrieveMedia.getAsUser(pref.avatarMediaId(), userId));
    } catch (Exception e) {
      log.warn(
          "Failed to enrich avatar for userId={}, avatarMediaId={}: {}",
          userId,
          pref.avatarMediaId(),
          e.getMessage());
      return pref;
    }
  }

  private static UserPreferencePatch validate(UserPreferencePatch body) {
    var username =
        body.username()
            .map(String::trim)
            .map(
                value -> {
                  if (value.isBlank()) {
                    throw new GlobalOpenCopException(
                        "Invalid username: must not be blank", HttpStatus.BAD_REQUEST);
                  }
                  return value;
                });

    var timezone =
        body.timezone()
            .map(
                value -> {
                  try {
                    var normalizedTimezone = ZoneId.of(value).getId();
                    if (!VALID_TIMEZONES.contains(normalizedTimezone)) {
                      throw new GlobalOpenCopException(
                          "Invalid timezone: " + value, HttpStatus.BAD_REQUEST);
                    }
                    return normalizedTimezone;
                  } catch (DateTimeException e) {
                    throw new GlobalOpenCopException(
                        "Invalid timezone: " + value, HttpStatus.BAD_REQUEST);
                  }
                });

    return new UserPreferencePatch(
        username,
        body.locale(),
        body.measurementSystem(),
        body.temperatureUnit(),
        timezone,
        body.zoomBehavior(),
        body.initialView(),
        body.defaultTileId(),
        body.avatarMediaId());
  }
}
