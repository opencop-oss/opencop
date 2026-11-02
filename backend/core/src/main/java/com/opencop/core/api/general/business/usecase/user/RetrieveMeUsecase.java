// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.user;

import com.opencop.core.api.general.business.model.auth.MeResponse;
import com.opencop.core.api.general.business.model.user.UserPreference;
import com.opencop.core.api.general.business.usecase.map.MapTileAccessUsecase;
import com.opencop.core.api.general.ports.gateway.repository.UserPreferenceRepository;
import com.opencop.core.api.general.ports.usecase.media.RetrieveMedia;
import com.opencop.core.api.general.technical.gateway.tile.MapTileSecretRef;
import com.opencop.core.sdk.ports.gateway.secrets.SecretStore;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

@Service
public class RetrieveMeUsecase {

  private static final Logger log = LoggerFactory.getLogger(RetrieveMeUsecase.class);
  private static final String TOKEN_KEY = "token";

  private final UserPreferenceRepository repository;
  private final SecretStore secretStore;
  private final RetrieveMedia retrieveMedia;
  private final MapTileAccessUsecase tileAccess;

  public RetrieveMeUsecase(
      UserPreferenceRepository repository,
      SecretStore secretStore,
      RetrieveMedia retrieveMedia,
      MapTileAccessUsecase tileAccess) {
    this.repository = repository;
    this.secretStore = secretStore;
    this.retrieveMedia = retrieveMedia;
    this.tileAccess = tileAccess;
  }

  public MeResponse get(Authentication authentication, OpenCopPrincipal principal) {
    var subject = principal.subject();
    var username = Optional.ofNullable(principal.preferredUsername()).orElse(principal.getName());
    var displayName =
        Optional.ofNullable(principal.fullName())
            .orElse(Optional.ofNullable(principal.givenName()).orElse(principal.getName()));
    var email = principal.email();
    var claims =
        subset(
            principal.getAttributes(),
            "sub",
            "preferred_username",
            "name",
            "given_name",
            "family_name",
            "email",
            "email_verified");
    var preference = repository.findByUserId(principal.internalUserId());
    preference = hydrateMapTileToken(preference, principal);
    preference = enrichAvatar(preference, principal.internalUserId());
    return new MeResponse(
        true,
        new MeResponse.User(principal.internalUserId(), subject, username, displayName, email),
        new MeResponse.Idp(principal.issuer()),
        getAuthorities(authentication),
        claims,
        preference);
  }

  /**
   * Resolves the default tile's token, but only for a caller who may still use that tile.
   *
   * <p>A default is stored as a bare id and outlives the access that justified it: the tile can be
   * disabled, its scope narrowed, or the grant revoked. Without this check the response hands back
   * the decrypted provider credential for a tile the caller can no longer see, which is the same
   * disclosure the tile detail route guards against. An unusable default is dropped rather than
   * returned tokenless, since the client cannot render it either way.
   */
  private UserPreference hydrateMapTileToken(
      UserPreference preference, OpenCopPrincipal principal) {
    var mapTile = preference.defaultTile();
    if (mapTile == null || mapTile.getId() == null) {
      return preference;
    }
    if (!tileAccess.canUse(mapTile.getId(), principal)) {
      return preference.withoutDefaultTile();
    }
    var secret = secretStore.read(MapTileSecretRef.fromTileId(mapTile.getId()));
    if (secret != null && secret.containsKey(TOKEN_KEY)) {
      mapTile.setToken(secret.get(TOKEN_KEY));
    } else {
      mapTile.setToken(null);
    }
    return preference;
  }

  private static List<String> getAuthorities(Authentication authentication) {
    return authentication.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .sorted(Comparator.naturalOrder())
        .toList();
  }

  private UserPreference enrichAvatar(UserPreference pref, UUID userId) {
    if (pref.avatarMediaId() == null) {
      return pref;
    }
    try {
      var avatar = retrieveMedia.getAsUser(pref.avatarMediaId(), userId);
      return pref.withAvatar(avatar);
    } catch (Exception e) {
      log.warn(
          "Failed to enrich avatar for userId={}, avatarMediaId={}",
          userId,
          pref.avatarMediaId(),
          e);
      return pref;
    }
  }

  private static Map<String, Object> subset(Map<String, Object> src, String... keys) {
    Map<String, Object> out = new LinkedHashMap<>();
    for (String key : keys) {
      if (src.containsKey(key) && src.get(key) != null) {
        out.put(key, src.get(key));
      }
    }
    return out;
  }
}
