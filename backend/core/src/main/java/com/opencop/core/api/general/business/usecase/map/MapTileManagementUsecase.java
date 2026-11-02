// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.map;

import com.opencop.core.api.general.business.policy.MapTilePolicy;
import com.opencop.core.api.general.ports.usecase.map.MapTileExceptionCodes;
import com.opencop.core.api.general.technical.gateway.tile.MapTileSecretRef;
import com.opencop.core.api.general.technical.gateway.tile.MapTileService;
import com.opencop.core.api.general.technical.gateway.tile.access.MapTileGrantService;
import com.opencop.core.api.general.technical.service.EffectiveMapTileAccessService;
import com.opencop.core.sdk.business.model.map.tile.MapTile;
import com.opencop.core.sdk.business.model.map.tile.MapTileConfig;
import com.opencop.core.sdk.business.model.map.tile.TileKind;
import com.opencop.core.sdk.business.model.map.tile.TileType;
import com.opencop.core.sdk.business.model.map.tile.WeatherTileExtension;
import com.opencop.core.sdk.business.model.map.tile.create.MapTileCreate;
import com.opencop.core.sdk.business.model.map.tile.patch.MapTileConfigPatch;
import com.opencop.core.sdk.business.model.map.tile.patch.MapTilePatch;
import com.opencop.core.sdk.business.model.map.tile.patch.WeatherTileExtensionPatch;
import com.opencop.core.sdk.business.model.map.tile.weather.WeatherFeatureConfig;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.ports.gateway.secrets.SecretStore;
import com.opencop.core.sdk.technical.gateway.db.iam.MapTileAccessScope;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class MapTileManagementUsecase {

  private static final String TOKEN_KEY = "token";
  private static final Pattern TILE_PLACEHOLDER = Pattern.compile("\\{[^}]+}");
  private static final Set<TileKind> WEATHER_TILE_KINDS =
      Set.of(TileKind.RASTER, TileKind.WMS, TileKind.WMTS);

  private final MapTileService service;
  private final MapTileGrantService grants;
  private final SecretStore secretStore;
  private final TransactionTemplate transaction;
  private final EffectiveMapTileAccessService effectiveAccess;

  public MapTileManagementUsecase(
      MapTileService service,
      MapTileGrantService grants,
      SecretStore secretStore,
      TransactionTemplate transaction,
      EffectiveMapTileAccessService effectiveAccess) {
    this.service = service;
    this.grants = grants;
    this.secretStore = secretStore;
    this.transaction = transaction;
    this.effectiveAccess = effectiveAccess;
  }

  public MapTile getPrimary(OpenCopPrincipal principal) {
    var primary = service.getPrimary();
    // Always passes while the primary tile stays GLOBAL. Kept so that if it ever does not, the
    // request fails loudly instead of serving a restricted tile to everyone.
    assertCanView(primary.getId(), principal);
    return primary;
  }

  public MapTile getOne(UUID id, OpenCopPrincipal principal) {
    // Existence first, so a missing tile is 404 for everyone rather than 403. Tile ids are not
    // sensitive.
    var mapTileOpt = service.getOne(id);
    if (mapTileOpt.isEmpty()) {
      throw new GlobalOpenCopException("Tile not found", HttpStatus.NOT_FOUND);
    }
    assertCanView(id, principal);
    var mapTile = mapTileOpt.get();
    // Disabled tiles are withheld from operators on this route too, not just from the list.
    // Reading by id would otherwise still hand back the url, config and decrypted token. Admins
    // keep access because the admin drawer edits disabled tiles.
    if (!mapTile.isEnabled() && !principal.isGlobalAdmin()) {
      throw new GlobalOpenCopException("Tile not found", HttpStatus.NOT_FOUND);
    }
    // The row's token reference is authoritative: a cleared token reads as none, even if a secret
    // is still stored under the tile's path. The unsanitized model carries that reference here, and
    // a reference whose secret is gone also reads as none, never as the reference itself.
    if (mapTile.getToken() != null) {
      var secret = this.secretStore.read(MapTileSecretRef.fromTileId(id));
      mapTile.setToken(secret == null ? null : secret.get(TOKEN_KEY));
    }
    return mapTile;
  }

  /**
   * The tiles the caller may put on a map: enabled, and either GLOBAL-scope or granted to them
   * directly or through a user group.
   *
   * <p>Deliberately not widened for a global admin. Administering a tile is a separate act from
   * carrying it on your own map, so holding the admin flag does not fill the caller's imagery
   * surfaces with every team's tiles. Effective layer access resolves operator visibility from
   * membership alone in the same way. The unfiltered catalogue is {@link #getAllForAdmin()}.
   */
  public List<MapTile> getAll(OpenCopPrincipal principal) {
    var enabled = service.getAllEnabled();
    var reachable = effectiveAccess.reachableTileIds(principal.internalUserId());
    List<MapTile> visible = new ArrayList<>();
    for (var tile : enabled) {
      if (reachable.contains(tile.getId())) {
        visible.add(tile);
      }
    }
    return visible;
  }

  public List<MapTile> getAllForAdmin() {
    return service.getAll();
  }

  public MapTile create(MapTileCreate body, OpenCopPrincipal principal) {
    return transaction.execute(_ -> createAndPersist(body, principal));
  }

  public MapTile patch(UUID id, MapTilePatch body, OpenCopPrincipal principal) {
    MapTilePolicy.assertMayManage(principal.isGlobalAdmin());
    MapTilePolicy.assertMayUseGlobalScope(
        body == null ? Optional.empty() : body.accessScope(), principal.isGlobalAdmin());
    return transaction.execute(_ -> patchAndPersist(id, body));
  }

  public void delete(UUID id, OpenCopPrincipal principal) {
    MapTilePolicy.assertMayManage(principal.isGlobalAdmin());
    MapTilePolicy.assertMayDelete(
        requireTile(id).isPrimary(), service.hasPromotableTileOtherThan(id));
    transaction.execute(
        _ -> {
          deleteMapTile(id);
          return null;
        });
  }

  public void setPrimary(UUID id) {
    var tile = requireTile(id);
    MapTilePolicy.assertMayBePrimary(
        tile.getConfig().tileType() == TileType.BASEMAP, tile.isEnabled(), tile.getAccessScope());
    service.setPrimary(id);
  }

  private MapTile createAndPersist(MapTileCreate body, OpenCopPrincipal principal) {
    if (body != null
        && body.accessScope() == MapTileAccessScope.GLOBAL
        && !principal.isGlobalAdmin()) {
      throw new GlobalOpenCopException(
          MapTileExceptionCodes.GLOBAL_SCOPE_REQUIRES_ADMIN.code(),
          MapTileExceptionCodes.GLOBAL_SCOPE_REQUIRES_ADMIN.description(),
          MapTileExceptionCodes.GLOBAL_SCOPE_REQUIRES_ADMIN.httpStatus());
    }
    if (body == null || body.config() == null) {
      throw new GlobalOpenCopException(
          MapTileExceptionCodes.CONFIG_REQUIRED.code(),
          MapTileExceptionCodes.CONFIG_REQUIRED.description(),
          MapTileExceptionCodes.CONFIG_REQUIRED.httpStatus());
    }
    if (!this.isValidUrl(body.url())) {
      throw new GlobalOpenCopException(
          MapTileExceptionCodes.BAD_URL.code(),
          MapTileExceptionCodes.BAD_URL.description(),
          MapTileExceptionCodes.BAD_URL.httpStatus());
    }
    validateConfig(body.config());
    // Surrounding whitespace is stripped once, here, so the length check sees what is stored.
    var attribution = body.attribution() == null ? null : body.attribution().strip();
    validateAttribution(attribution);
    UUID id = UUID.randomUUID();
    assertNameFree(body.name(), id);
    // A blank token means none, as on PATCH: no secret is written and the row keeps no reference.
    String tokenRef = null;
    if (hasToken(body.token())) {
      tokenRef = MapTileSecretRef.fromTileId(id);
      this.secretStore.patch(tokenRef, Map.of(TOKEN_KEY, body.token()), null);
    }
    // The service receives the secret's reference in the token slot, never the token itself. Every
    // other field is carried through: the five-argument constructor would default accessScope to
    // null, which the mapper reads as PRIVATE, silently downgrading a requested GLOBAL create.
    var mapTile =
        new MapTileCreate(
            body.name(),
            body.url(),
            body.description(),
            tokenRef,
            body.accessScope(),
            body.config(),
            attribution);
    var created = service.create(id, mapTile);
    // Grant the creator access, so a new PRIVATE tile is visible to the person who made it.
    grants.assignMembers(id, List.of(principal.internalUserId()));
    return created;
  }

  private MapTile patchAndPersist(UUID id, MapTilePatch body) {
    if (body == null || body.url().isPresent() && !this.isValidUrl(body.url().get())) {
      throw new GlobalOpenCopException(
          MapTileExceptionCodes.BAD_URL.code(),
          MapTileExceptionCodes.BAD_URL.description(),
          MapTileExceptionCodes.BAD_URL.httpStatus());
    }
    var current = requireTile(id);
    validatePatch(current, body);
    body.name().ifPresent(name -> assertNameFree(name, id));
    // Stripped once, here; a blank credit stays present as an empty string, so it still clears.
    var attribution = body.attribution().map(String::strip);
    attribution.ifPresent(MapTileManagementUsecase::validateAttribution);
    // A blank token clears it: JSON null reads as an absent field on this API, so it cannot. The
    // service receives the token slot resolved to the secret's reference, or to a blank that clears
    // the row's reference, never the token itself.
    var tokenRef = MapTileSecretRef.fromTileId(id);
    var rowPatch =
        new MapTilePatch(
            body.name(),
            body.url(),
            body.description(),
            body.token().map(token -> hasToken(token) ? tokenRef : ""),
            body.enabled(),
            body.accessScope(),
            body.config(),
            attribution);
    // The row is written first, so an edit the database rejects never reaches the secret store.
    var patched = this.service.patch(id, rowPatch);
    body.token()
        .ifPresent(
            token -> {
              if (hasToken(token)) {
                this.secretStore.patch(tokenRef, Map.of(TOKEN_KEY, token), null);
              } else {
                this.secretStore.deleteAllVersions(tokenRef);
              }
            });
    return patched;
  }

  private void deleteMapTile(UUID id) {
    this.secretStore.deleteAllVersions(MapTileSecretRef.fromTileId(id));
    service.delete(id);
  }

  /** Resolves whether a grant reaches the caller, then asks the policy. */
  private void assertCanView(UUID mapTileId, OpenCopPrincipal principal) {
    MapTilePolicy.assertCanView(
        effectiveAccess.canReach(mapTileId, principal.internalUserId()), principal.isGlobalAdmin());
  }

  private MapTile requireTile(UUID id) {
    return service
        .getOneSanitized(id)
        .orElseThrow(() -> new GlobalOpenCopException("Tile not found", HttpStatus.NOT_FOUND));
  }

  /**
   * Tile names are unique. Checking before any write answers a taken name with a 409, rather than
   * the unique constraint's generic 500, and keeps a create from writing its secret first.
   */
  private void assertNameFree(String name, UUID id) {
    if (name != null && service.isNameTakenByAnotherTile(name, id)) {
      throw new GlobalOpenCopException(
          MapTileExceptionCodes.NAME_TAKEN.code(),
          MapTileExceptionCodes.NAME_TAKEN.description(),
          MapTileExceptionCodes.NAME_TAKEN.httpStatus());
    }
  }

  /** A blank token means no token, on create and on PATCH alike. */
  private static boolean hasToken(String token) {
    return token != null && !token.isBlank();
  }

  private static void validateAttribution(String attribution) {
    if (attribution != null && attribution.length() > MapTile.MAX_ATTRIBUTION_LENGTH) {
      throw new GlobalOpenCopException(
          MapTileExceptionCodes.ATTRIBUTION_TOO_LONG.code(),
          MapTileExceptionCodes.ATTRIBUTION_TOO_LONG.description(),
          MapTileExceptionCodes.ATTRIBUTION_TOO_LONG.httpStatus());
    }
  }

  private void validateConfig(MapTileConfig config) {
    if (config == null || config.getTile() == null) {
      throw new GlobalOpenCopException(
          MapTileExceptionCodes.CONFIG_REQUIRED.code(),
          MapTileExceptionCodes.CONFIG_REQUIRED.description(),
          MapTileExceptionCodes.CONFIG_REQUIRED.httpStatus());
    }

    if (config.tileType() == TileType.WEATHER) {
      if (!WEATHER_TILE_KINDS.contains(config.kind())) {
        throw new GlobalOpenCopException(
            MapTileExceptionCodes.INVALID_WEATHER_KIND.code(),
            MapTileExceptionCodes.INVALID_WEATHER_KIND.description(),
            MapTileExceptionCodes.INVALID_WEATHER_KIND.httpStatus());
      }
      if (config.getExtension() == null) {
        throw new GlobalOpenCopException(
            MapTileExceptionCodes.EXTENSION_REQUIRED.code(),
            MapTileExceptionCodes.EXTENSION_REQUIRED.description(),
            MapTileExceptionCodes.EXTENSION_REQUIRED.httpStatus());
      }
      validateWeatherExtension(asWeatherExtension(config.getExtension()));
      return;
    }

    if (config.getExtension() != null) {
      throw new GlobalOpenCopException(
          MapTileExceptionCodes.EXTENSION_NOT_ALLOWED.code(),
          MapTileExceptionCodes.EXTENSION_NOT_ALLOWED.description(),
          MapTileExceptionCodes.EXTENSION_NOT_ALLOWED.httpStatus());
    }
  }

  private void validatePatch(MapTile current, MapTilePatch body) {
    if (body.config().isPresent()
        && body.config().get().kind().isPresent()
        && body.config().get().tile().isEmpty()) {
      throw new GlobalOpenCopException(
          MapTileExceptionCodes.KIND_CHANGE_REQUIRES_CONFIG.code(),
          MapTileExceptionCodes.KIND_CHANGE_REQUIRES_CONFIG.description(),
          MapTileExceptionCodes.KIND_CHANGE_REQUIRES_CONFIG.httpStatus());
    }

    TileKind resultingKind = resolveTileKind(current.getConfig(), body.config());
    TileType resultingType = resolveTileType(current.getConfig(), body.config());

    if (current.isPrimary() && resultingType != TileType.BASEMAP) {
      throw new GlobalOpenCopException(
          MapTileExceptionCodes.PRIMARY_MUST_BE_BASEMAP.code(),
          MapTileExceptionCodes.PRIMARY_MUST_BE_BASEMAP.description(),
          MapTileExceptionCodes.PRIMARY_MUST_BE_BASEMAP.httpStatus());
    }

    // /v1/map/tiles/primary must resolve for every operator, so the primary tile cannot be
    // disabled. Promote another basemap first.
    if (current.isPrimary() && body.enabled().filter(enabled -> !enabled).isPresent()) {
      throw enabledPrimaryRequired();
    }

    // Same for scope: a PRIVATE primary would leave ungranted operators with no basemap.
    if (current.isPrimary()
        && body.accessScope().filter(scope -> scope == MapTileAccessScope.PRIVATE).isPresent()) {
      throw new GlobalOpenCopException(
          MapTileExceptionCodes.PRIMARY_MUST_BE_GLOBAL.code(),
          MapTileExceptionCodes.PRIMARY_MUST_BE_GLOBAL.description(),
          MapTileExceptionCodes.PRIMARY_MUST_BE_GLOBAL.httpStatus());
    }

    if (resultingType == TileType.WEATHER) {
      if (!WEATHER_TILE_KINDS.contains(resultingKind)) {
        throw new GlobalOpenCopException(
            MapTileExceptionCodes.INVALID_WEATHER_KIND.code(),
            MapTileExceptionCodes.INVALID_WEATHER_KIND.description(),
            MapTileExceptionCodes.INVALID_WEATHER_KIND.httpStatus());
      }
      if (!willHaveExtension(current.getConfig(), body.config())) {
        throw new GlobalOpenCopException(
            MapTileExceptionCodes.EXTENSION_REQUIRED.code(),
            MapTileExceptionCodes.EXTENSION_REQUIRED.description(),
            MapTileExceptionCodes.EXTENSION_REQUIRED.httpStatus());
      }
      validateWeatherExtension(resolveWeatherExtension(current.getConfig(), body.config()));
      return;
    }

    if (body.config().isPresent() && body.config().get().extension().isPresent()) {
      throw new GlobalOpenCopException(
          MapTileExceptionCodes.EXTENSION_NOT_ALLOWED.code(),
          MapTileExceptionCodes.EXTENSION_NOT_ALLOWED.description(),
          MapTileExceptionCodes.EXTENSION_NOT_ALLOWED.httpStatus());
    }
  }

  private GlobalOpenCopException enabledPrimaryRequired() {
    return new GlobalOpenCopException(
        MapTileExceptionCodes.PRIMARY_MUST_BE_ENABLED.code(),
        MapTileExceptionCodes.PRIMARY_MUST_BE_ENABLED.description(),
        MapTileExceptionCodes.PRIMARY_MUST_BE_ENABLED.httpStatus());
  }

  private TileKind resolveTileKind(
      MapTileConfig currentConfig, Optional<MapTileConfigPatch> configPatch) {
    if (configPatch.isPresent() && configPatch.get().kind().isPresent()) {
      return configPatch.get().kind().get();
    }
    return currentConfig.kind();
  }

  private TileType resolveTileType(
      MapTileConfig currentConfig, Optional<MapTileConfigPatch> configPatch) {
    if (configPatch.isPresent() && configPatch.get().type().isPresent()) {
      return configPatch.get().type().get();
    }
    return currentConfig.tileType();
  }

  private boolean willHaveExtension(
      MapTileConfig currentConfig, Optional<MapTileConfigPatch> configPatch) {
    if (configPatch.isEmpty()) {
      return currentConfig.getExtension() != null;
    }
    if (configPatch.get().extension().isPresent()) {
      return configPatch.get().extension().get() != null;
    }
    return currentConfig.getExtension() != null;
  }

  private boolean isValidUrl(String value) {
    if (value == null || value.isBlank()) {
      return true;
    }

    try {
      String normalized = TILE_PLACEHOLDER.matcher(value).replaceAll("0");
      URI uri = new URI(normalized);
      String scheme = uri.getScheme();
      if (scheme == null
          || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
        return false;
      }
      return uri.getHost() != null;
    } catch (Exception _) {
      return false;
    }
  }

  private void validateWeatherExtension(WeatherTileExtension extension) {
    if (extension == null || extension.features() == null || extension.features().isEmpty()) {
      return;
    }

    Set<String> featureIds = new HashSet<>();
    for (WeatherFeatureConfig feature : extension.features()) {
      if (feature == null || StringUtils.isBlank(feature.feature())) {
        throw new GlobalOpenCopException(
            MapTileExceptionCodes.WEATHER_FEATURE_REQUIRED.code(),
            MapTileExceptionCodes.WEATHER_FEATURE_REQUIRED.description(),
            MapTileExceptionCodes.WEATHER_FEATURE_REQUIRED.httpStatus());
      }
      if (StringUtils.isBlank(feature.url()) || !isValidUrl(feature.url())) {
        throw new GlobalOpenCopException(
            MapTileExceptionCodes.WEATHER_FEATURE_URL_INVALID.code(),
            MapTileExceptionCodes.WEATHER_FEATURE_URL_INVALID.description(),
            MapTileExceptionCodes.WEATHER_FEATURE_URL_INVALID.httpStatus());
      }
      if (!featureIds.add(feature.feature())) {
        throw new GlobalOpenCopException(
            MapTileExceptionCodes.DUPLICATE_WEATHER_FEATURE.code(),
            MapTileExceptionCodes.DUPLICATE_WEATHER_FEATURE.description(),
            MapTileExceptionCodes.DUPLICATE_WEATHER_FEATURE.httpStatus());
      }
    }
  }

  private WeatherTileExtension resolveWeatherExtension(
      MapTileConfig currentConfig, Optional<MapTileConfigPatch> configPatch) {
    if (configPatch.isPresent() && configPatch.get().extension().isPresent()) {
      var extensionPatch = configPatch.get().extension().get();
      if (extensionPatch instanceof WeatherTileExtensionPatch weatherPatch
          && weatherPatch.features().isPresent()) {
        return new WeatherTileExtension(weatherPatch.features().get());
      }
    }
    return asWeatherExtension(currentConfig.getExtension());
  }

  private WeatherTileExtension asWeatherExtension(Object extension) {
    return extension instanceof WeatherTileExtension weather ? weather : null;
  }
}
