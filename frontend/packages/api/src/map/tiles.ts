// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { apiFetch, type FetchOptions } from "../client";
import type { GroupSource } from "../groups/groups";

export const TileKind = {
    VECTOR: "VECTOR",
    RASTER: "RASTER",
    WMS: "WMS",
    WMTS: "WMTS",
    RASTER_DEM: "RASTER_DEM",
    TERRAIN_MESH: "TERRAIN_MESH",
    TILE_3D: "TILE_3D",
} as const;

export type TileKind = (typeof TileKind)[keyof typeof TileKind];

export type TileType = "BASEMAP" | "WEATHER";

export type WeatherFeatureId = string;

export interface WeatherFeatureEntry {
    feature: WeatherFeatureId;
    url: string;
}

export interface WeatherTileExtension {
    extensionType: "WEATHER";
    features: WeatherFeatureEntry[];
}

export interface VectorTileConfig {
    kind: "VECTOR";
    tileSize: number;
    minZoom: number | null;
    maxZoom: number | null;
}

export interface RasterTileConfig {
    kind: "RASTER";
    tileSize: number;
    minZoom: number | null;
    maxZoom: number | null;
}

export interface WmsTileConfig {
    kind: "WMS";
    version: string;
    layers: string[];
    styles: string[] | null;
    format: string;
    transparent: boolean;
    crs: string;
    extraParams?: Record<string, string> | null;
}

export interface WmtsTileConfig {
    kind: "WMTS";
    version: string;
    layer: string;
    style: string;
    format: string;
    tileMatrixSet: string;
    extraParams?: Record<string, string> | null;
}

export interface RasterDemTileConfig {
    kind: "RASTER_DEM";
    tileSize: number;
    minZoom: number | null;
    maxZoom: number | null;
    encoding: "mapbox" | "terrarium" | "custom";
    redFactor?: number;
    greenFactor?: number;
    blueFactor?: number;
    baseShift?: number;
}

export interface ElevationDecoderConfig {
    rScaler: number;
    gScaler: number;
    bScaler: number;
    offset: number;
}

export interface TerrainMeshTileConfig {
    kind: "TERRAIN_MESH";
    elevationUrl?: string;
    encoding: "TERRARIUM" | "TERRAIN_RGB";
    decoderConfig?: ElevationDecoderConfig;
    exaggeration?: number;
    meshMaxError?: number;
    boundsPadding?: number;
}

/** DeckGL Tile3DLayer config. */
export interface Tile3dTileConfig {
    kind: "TILE_3D";
    loaderType: "cesium" | "arcgis" | "google" | "standard";
    pointSize?: number;
    opacity?: number;
}

export type TileConfig =
    | VectorTileConfig
    | RasterTileConfig
    | WmsTileConfig
    | WmtsTileConfig
    | RasterDemTileConfig
    | TerrainMeshTileConfig
    | Tile3dTileConfig;

export type WeatherCapableTileConfig = Extract<TileConfig, { kind: "RASTER" | "WMS" | "WMTS" }>;

export interface BasemapMapTileConfig {
    type: "BASEMAP";
    extension?: null;
    tile: TileConfig;
}

export interface WeatherMapTileConfig {
    type: "WEATHER";
    extension: WeatherTileExtension;
    tile: WeatherCapableTileConfig;
}

export type MapTileConfig = BasemapMapTileConfig | WeatherMapTileConfig;

export interface UpdateMapTileConfig {
    type?: TileType;
    extension?: WeatherTileExtension | null;
    kind?: TileKind;
    tile?: TileConfig;
}

// ---------------------------------------------------------------------------
// API interfaces
// ---------------------------------------------------------------------------

export type MapTileAccessScope = "PRIVATE" | "GLOBAL";

/**
 * A user's grant on a tile. Holding one means the user can see the tile; imagery is managed by
 * global admins, so a grant carries no role. Identity fields are null when the user is not
 * provisioned locally.
 */
export interface MapTileMember {
    mapTileId: string;
    userId: string;
    grantedAt: string;
    username: string | null;
    firstName: string | null;
    lastName: string | null;
    email: string | null;
}

/** A user group's grant on a tile: every member of the group can see it. */
export interface MapTileUserGroupGrant {
    mapTileId: string;
    userGroupId: string;
    grantedAt: string;
    name: string;
    source: GroupSource;
}

/**
 * Map tile configuration returned from the API.
 */
export interface MapTile {
    id: string;
    primary: boolean;
    /** When false the tile is retained but withheld from every operator-facing surface. */
    enabled: boolean;
    accessScope: MapTileAccessScope;
    name: string;
    description: string;
    url: string;
    token: string | null;
    config: MapTileConfig;
    /**
     * Credit for this tile, or null: free text that may contain `<a href>` links, as provider
     * credits often do. Stored as entered and checked for length only, so it is untrusted: reduce
     * it to text and http(s) links before display, and never insert it as HTML.
     */
    attribution: string | null;
}

/** The longest `attribution` the API accepts (MAP-TILE-1016). */
export const MAX_TILE_ATTRIBUTION_LENGTH = 1000;

/**
 * Request body for creating a new map tile.
 */
export interface CreateMapTile {
    url: string;
    name: string;
    description: string;
    token?: string;
    /** Omitted means PRIVATE. GLOBAL is rejected for non-admins. */
    accessScope?: MapTileAccessScope;
    config: MapTileConfig;
    attribution?: string;
}

/**
 * Request body for updating a map tile.
 * Only fields that need to be updated should be present.
 * The API reads null as an absent field, so null never clears anything; the optional `token` and
 * `attribution` clear with an empty string.
 */
export interface UpdateMapTile {
    url?: string | null;
    name?: string | null;
    description?: string | null;
    /** An empty string clears the token; the API reads null as an absent field. */
    token?: string;
    enabled?: boolean;
    accessScope?: MapTileAccessScope;
    config?: UpdateMapTileConfig;
    /** An empty string clears the credit; the API reads null as an absent field. */
    attribution?: string;
}

/** The catalog as operators see it. The server withholds disabled tiles. */
export async function getTiles(options?: FetchOptions): Promise<MapTile[]> {
    return apiFetch<MapTile[]>("/map/tiles", { signal: options?.signal });
}

/** The full catalog including disabled tiles. Admin-only, 403s for everyone else. */
export async function getAdminTiles(options?: FetchOptions): Promise<MapTile[]> {
    return apiFetch<MapTile[]>("/admin/map/tiles", { signal: options?.signal });
}

export async function getTile(id: string, options?: FetchOptions): Promise<MapTile> {
    return apiFetch<MapTile>(`/map/tiles/${id}`, { signal: options?.signal });
}

export async function createTile(data: CreateMapTile): Promise<MapTile> {
    return apiFetch<MapTile>("/admin/map/tiles", {
        method: "POST",
        body: JSON.stringify(data),
    });
}

/**
 * Resource-scoped rather than admin-scoped, unlike its neighbours here. Tile ADMIN is enough, and
 * everything under /admin is gated on the global admin role before it reaches a controller. The
 * use case authorizes these per tile instead.
 */
export async function updateTile(id: string, data: UpdateMapTile): Promise<MapTile> {
    return apiFetch<MapTile>(`/map/tiles/${id}`, {
        method: "PATCH",
        body: JSON.stringify(data),
    });
}

export async function makeTilePrimary(id: string): Promise<void> {
    return apiFetch<void>(`/admin/map/tiles/${id}/primary`, {
        method: "PUT",
    });
}

/** Resource-scoped for the same reason as {@link updateTile}, but requires tile OWNER. */
export async function deleteTile(id: string): Promise<void> {
    return apiFetch<void>(`/map/tiles/${id}`, {
        method: "DELETE",
    });
}

// ---------------------------------------------------------------------------
// Grants
// ---------------------------------------------------------------------------

export async function getTileMembers(id: string, options?: FetchOptions): Promise<MapTileMember[]> {
    return apiFetch<MapTileMember[]>(`/map/tiles/${id}/members`, { signal: options?.signal });
}

/** Grants each user access to the tile. Re-granting an existing member is a no-op. */
export async function assignTileMembers(id: string, userIds: string[]): Promise<void> {
    return apiFetch<void>(`/map/tiles/${id}/members`, {
        method: "POST",
        body: JSON.stringify(userIds),
    });
}

export async function revokeTileMembers(id: string, userIds: string[]): Promise<void> {
    return apiFetch<void>(`/map/tiles/${id}/members`, {
        method: "DELETE",
        body: JSON.stringify(userIds),
    });
}

export async function getTileUserGroups(
    id: string,
    options?: FetchOptions,
): Promise<MapTileUserGroupGrant[]> {
    return apiFetch<MapTileUserGroupGrant[]>(`/map/tiles/${id}/user-groups`, {
        signal: options?.signal,
    });
}

export async function assignTileUserGroups(id: string, userGroupIds: string[]): Promise<void> {
    return apiFetch<void>(`/map/tiles/${id}/user-groups`, {
        method: "POST",
        body: JSON.stringify(userGroupIds),
    });
}

export async function revokeTileUserGroups(id: string, userGroupIds: string[]): Promise<void> {
    return apiFetch<void>(`/map/tiles/${id}/user-groups`, {
        method: "DELETE",
        body: JSON.stringify(userGroupIds),
    });
}
