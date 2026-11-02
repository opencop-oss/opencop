// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.tile;

import com.opencop.core.api.general.business.policy.MapTilePolicy;
import com.opencop.core.api.general.ports.usecase.map.MapTileExceptionCodes;
import com.opencop.core.sdk.business.model.map.tile.MapTile;
import com.opencop.core.sdk.business.model.map.tile.MapTileConfig;
import com.opencop.core.sdk.business.model.map.tile.TileKind;
import com.opencop.core.sdk.business.model.map.tile.TileType;
import com.opencop.core.sdk.business.model.map.tile.create.MapTileCreate;
import com.opencop.core.sdk.business.model.map.tile.patch.MapTilePatch;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import jakarta.persistence.PersistenceException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MapTileService {

  private static final Set<TileKind> BASEMAP_KINDS =
      Set.of(TileKind.VECTOR, TileKind.RASTER, TileKind.WMS, TileKind.WMTS);

  private final MapTileRepository repo;

  public MapTileService(MapTileRepository repo) {
    this.repo = repo;
  }

  @Transactional
  public MapTile create(UUID id, MapTileCreate create) {
    // A tile is always created enabled, so eligibility reduces to the other two conditions.
    boolean shouldBePrimary =
        !repo.existsByIsPrimaryTrue()
            && MapTilePolicy.canBePrimary(isBasemap(create.config()), true, create.accessScope());
    MapTileEntity entity = MapTileMapper.toNewEntity(id, create, shouldBePrimary);
    MapTileEntity saved = repo.save(entity);
    return MapTileMapper.toSanitizedModel(saved);
  }

  @Transactional
  public MapTile patch(UUID id, MapTilePatch patch) {
    MapTileEntity entity =
        repo.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("map_tile not found: " + id));

    MapTileMapper.applyPatch(patch, entity);
    if (entity.isPrimary() && !isBasemap(entity.getConfig())) {
      throw new IllegalArgumentException("Primary tile must be a basemap");
    }

    // Flushed so a constraint failure, such as a duplicate name, surfaces here, before the caller
    // changes the secret store.
    MapTileEntity saved = repo.saveAndFlush(entity);
    return MapTileMapper.toSanitizedModel(saved);
  }

  /**
   * Whether some other tile could take over as primary if {@code id} went away. Shares {@link
   * #canBecomePrimary} with the auto-promotion below, so the guard and the promotion can never
   * disagree about what counts as eligible.
   */
  @Transactional(readOnly = true)
  public boolean hasPromotableTileOtherThan(UUID id) {
    return repo.findAll().stream()
        .filter(tile -> !tile.getId().equals(id))
        .anyMatch(MapTileService::canBecomePrimary);
  }

  /** Whether a tile other than {@code id} already has {@code name}; tile names are unique. */
  @Transactional(readOnly = true)
  public boolean isNameTakenByAnotherTile(String name, UUID id) {
    return repo.existsByNameAndIdNot(name, id);
  }

  @Transactional
  public void delete(UUID id) {
    MapTileEntity entity =
        repo.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("map_tile not found: " + id));
    boolean wasPrimary = entity.isPrimary();
    repo.delete(entity);
    repo.flush();
    if (wasPrimary) {
      repo.findAll().stream()
          .filter(MapTileService::canBecomePrimary)
          .findFirst()
          .ifPresent(
              mapTileEntity -> {
                mapTileEntity.setPrimary(true);
                this.repo.save(mapTileEntity);
              });
    }
  }

  @Transactional(readOnly = true)
  public Optional<MapTile> getOne(UUID id) throws PersistenceException {
    return repo.findById(id).map(MapTileMapper::toModel);
  }

  @Transactional(readOnly = true)
  public Optional<MapTile> getOneSanitized(UUID id) throws PersistenceException {
    return repo.findById(id).map(MapTileMapper::toSanitizedModel);
  }

  /** The full catalog, disabled tiles included. Admin surfaces only. */
  @Transactional(readOnly = true)
  public List<MapTile> getAll() {
    return repo.findAll().stream().map(MapTileMapper::toSanitizedModel).toList();
  }

  /** The catalog as operators see it. Disabled tiles are withheld. */
  @Transactional(readOnly = true)
  public List<MapTile> getAllEnabled() {
    return repo.findAllByEnabledTrue().stream().map(MapTileMapper::toSanitizedModel).toList();
  }

  /**
   * The primary basemap. Absent only on a catalog that has never had one deleting the primary is
   * rejected, and disabling or privatising it is too — so this reports 404 rather than failing as
   * an internal error.
   */
  @Transactional(readOnly = true)
  public MapTile getPrimary() {
    return repo.findByIsPrimaryTrue()
        .map(MapTileMapper::toModel)
        .orElseThrow(
            () ->
                new GlobalOpenCopException(
                    MapTileExceptionCodes.PRIMARY_NOT_SET.code(),
                    MapTileExceptionCodes.PRIMARY_NOT_SET.description(),
                    MapTileExceptionCodes.PRIMARY_NOT_SET.httpStatus()));
  }

  @Transactional
  public void setPrimary(UUID id) {
    repo.setPrimaryByProcedure(id);
  }

  /**
   * Auto-promotion writes {@code is_primary} straight onto the entity, bypassing the guards {@code
   * setPrimary} applies, so it asks the policy for the same eligibility rule.
   */
  private static boolean canBecomePrimary(MapTileEntity tile) {
    return MapTilePolicy.canBePrimary(
        isBasemap(tile.getConfig()), tile.isEnabled(), tile.getAccessScope());
  }

  private static boolean isBasemap(MapTileConfig config) {
    return config != null
        && config.tileType() == TileType.BASEMAP
        && config.kind() != null
        && BASEMAP_KINDS.contains(config.kind());
  }
}
