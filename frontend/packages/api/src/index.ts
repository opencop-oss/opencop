// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

export { apiFetch, clearCsrfToken, withRequestTimeout } from "./client.ts";
export type {
    ApiFetchOptions,
    FetchOptions,
    IgnoreSuccessfulResponseBodyOptions,
    JsonResponseOptions,
} from "./client.ts";
export { ApiError, getErrorMessage, isNonRetryableAuthError } from "./error.ts";
export type { ApiErrorBody } from "./error.ts";

export { getAppMetadata } from "./app-metadata/app-metadata.ts";
export type {
    AppMetadata,
    RuntimeFeature,
    RuntimeFeatureAudience,
    RuntimeFeatureAvailability,
} from "./app-metadata/app-metadata.ts";

export { getAuditHistory } from "./audit/audit.ts";
export type {
    AuditActor,
    AuditChange,
    AuditDelta,
    AuditDeltaEntry,
    AuditEntityType,
    AuditFieldChange,
    AuditHistoryItem,
    AuditHistoryPageParams,
    AuditOperation,
} from "./audit/audit.ts";

export {
    getRuntimeFeatureHistory,
    isRuntimeFeatureConflict,
    patchRuntimeFeatureAudience,
    reconcileRuntimeFeatures,
} from "./runtime-features/runtime-features.ts";
export type {
    RuntimeFeatureAudiencePatch,
    RuntimeFeatureHistoryActor,
    RuntimeFeatureHistoryItem,
    RuntimeFeatureHistoryOperation,
    RuntimeFeatureHistoryPageParams,
    RuntimeFeatureReconciliation,
    RuntimeFeatureRejection,
} from "./runtime-features/runtime-features.ts";

export {
    createBroadcastEvent,
    patchBroadcastEvent,
    deleteBroadcastEvent,
    patchSourceBroadcastEvent,
    deleteSourceBroadcastEvent,
    fetchEventDetailsLatest,
    fetchEventHistory,
    addRemark,
    updateRemark,
    deleteRemark,
    retrieveRemarks,
    pushEvent,
    uploadEventAttachment,
    registerEventAttachmentUrl,
    listEventAttachments,
    deleteEventAttachment,
} from "./events/events.ts";

export type {
    BroadcastEventRequest,
    PatchBroadcastEventRequest,
    DeleteBroadcastEventRequest,
    PatchSourceBroadcastEventRequest,
    DeleteSourceBroadcastEventRequest,
    EventDetails,
    EventHistoryResponse,
    HistoryFeature,
    HistoryFeatureCollection,
    HistoryParams,
    OpenCOPElement,
    OpenCOPProperties,
    SamplingMeta,
    Remark,
    Contact,
    CreateRemarkRequest,
    UpdateRemarkRequest,
    FileAttachment,
    UrlAttachment,
    UrlAttachmentRecord,
    StreamKind,
    EventAttachment,
    MediaRecord,
    MediaImageAttributes,
    ThumbnailRef,
} from "./events/events.ts";

export {
    fetchSourceHistogram,
    submitHistoricalTracks,
    HISTOGRAM_METRIC,
} from "./events/historical.ts";
export type {
    HistogramBucket,
    HistogramBucketValue,
    HistogramMetric,
    HistogramParams,
    HistogramResponse,
    HistoricalSubjectRequest,
    HistoricalFilterRequest,
    HistoricalTracksRequest,
    HistoricalSubjectAck,
    HistoricalTracksAck,
} from "./events/historical.ts";

export {
    assignUserToLayer,
    cloneLayer,
    createLayer,
    deleteLayer,
    getDataSources as getLayerDataSources,
    getLayer,
    getLayers,
    getMembers,
    getMyLayers,
    importLayerFile,
    patchLayer,
    removeUserFromLayer,
    updateLayerMembership,
} from "./layers/layers.ts";
export { LayerRoles, getUserGroupsForLayer, removeUserGroupFromLayer } from "./layers/layers.ts";
export type {
    AccessScope,
    CloneLayerRequest,
    CreateLayer,
    LayerUserGroupAssignment,
    LayerInfo,
    LayerRole,
    LayerUser,
    MyLayerInfo,
} from "./layers/layers.ts";
export type {
    RecordVisibility,
    RecordQueryOptions,
    Page,
    PageRequestParams,
    SortParam,
    FilterParam,
} from "./common.ts";
export {
    buildPageParams,
    resolveInfiniteRetry,
    MAX_FILTER_CRITERIA,
    MAX_FILTER_VALUE_LENGTH,
} from "./common.ts";

export {
    DataSourceRoles,
    addLayersToDataSource,
    addUsersToDataSource,
    deleteDataSource,
    deleteLayerFromDataSource,
    deleteUserFromDataSource,
    disconnectDataSource,
    getDataSource,
    getDataSources,
    getLayersForDataSource,
    getMyDataSources,
    getSourceCandidatesForLayer,
    getUsersForDataSource,
    patchDataSource,
    reconnectDataSource,
    createConnectionV2,
    INTEGRATION_UNAVAILABLE,
} from "./data-sources/data-sources.ts";
export type {
    AccessMode,
    ConnectionCreatedV2Response,
    ConnectionIntegrationRef,
    ConnectionSetup,
    ConnectionState,
    ConnectionStateReason,
    CreateConnectionV2Request,
    CreateMapperConnectionRequest,
    CreateTcpConnectionRequest,
    TcpFramingSelection,
    DataSourceInfo,
    DataSourceLayer,
    DataSourcePatch,
    DataSourceRole,
    DataSourceUser,
    KafkaCredentials,
    MyDataSource,
    SourceCandidate,
    TransportType,
} from "./data-sources/data-sources.ts";

export {
    createMapperProfile,
    deleteMapperProfile,
    getMapperProfileFullMeta,
    getMapperProfileVersions,
    getEgressMapperProfiles,
    getMapperProfiles,
    getMapperProfilesAdmin,
    previewMapper,
    updateMapperProfile,
} from "./data-sources/profiles.ts";
export type {
    MapperProfile,
    MapperProfileFullMeta,
    MapperProfileFullMetaRequest,
    MapperProfilePatch,
    MapperProfileRequest,
} from "./data-sources/profiles.ts";

export { getIntegrationConnectionSetup, getIntegrations } from "./data-sources/integrations.ts";
export type {
    ConnectionExtractor,
    EgressRule,
    ExtractorField,
    ExtractorFieldKind,
    FramingMode,
    IntegrationCatalogEntry,
    IntegrationConnectionSetup,
    IntegrationFraming,
    MapperIngressRule,
    ProtocolSetup,
    StandardExtractorField,
    TcpIngressRule,
} from "./data-sources/integrations.ts";

export { TARGET_FIELD_META } from "./data-sources/mapper.model.ts";
export type {
    CsvFormatConfig,
    DynamicMapper,
    FormatConfig,
    FormatType,
    KmlFormatConfig,
    MappedRecord,
    MappingError,
    MappingResponse,
    MappingRule,
    MappingWarning,
    OnMissing,
    PreviewMappingRequest,
    RecordSelector,
    SourceRef,
    TargetField,
    TransformOperation,
    TransformStep,
    XmlFormatConfig,
} from "./data-sources/mapper.model.ts";

export {
    getAssignedDataSources,
    getAssignedLayers,
    getUserById,
    getUsersPage,
    updateUserAdmin,
} from "./users/users.ts";
export type { AssignedDataSource, AssignedLayer, User, UsersPageParams } from "./users/users.ts";

export { getUserGroupsPage } from "./groups/groups.ts";
export type { GroupSource, UserGroupSearchResult, UserGroupsPageParams } from "./groups/groups.ts";

export { getUserDirectoryPage } from "./user-directory/user-directory.ts";
export type {
    UserDirectoryEntry,
    UserDirectoryPageParams,
} from "./user-directory/user-directory.ts";

export { getLayerDirectoryPage } from "./layer-directory/layer-directory.ts";
export type {
    LayerDirectoryEntry,
    LayerDirectoryPageParams,
} from "./layer-directory/layer-directory.ts";

export {
    assignTileMembers,
    assignTileUserGroups,
    createTile,
    deleteTile,
    getAdminTiles,
    getTile,
    getTileMembers,
    getTileUserGroups,
    getTiles,
    makeTilePrimary,
    revokeTileMembers,
    revokeTileUserGroups,
    updateTile,
} from "./map/tiles.ts";
export type {
    CreateMapTile,
    ElevationDecoderConfig,
    MapTileAccessScope,
    MapTileConfig,
    MapTile,
    MapTileMember,
    MapTileUserGroupGrant,
    RasterDemTileConfig,
    RasterTileConfig,
    TerrainMeshTileConfig,
    Tile3dTileConfig,
    TileConfig,
    TileType,
    UpdateMapTile,
    UpdateMapTileConfig,
    VectorTileConfig,
    WeatherCapableTileConfig,
    WeatherFeatureEntry,
    WeatherFeatureId,
    WeatherTileExtension,
    WmsTileConfig,
    WmtsTileConfig,
} from "./map/tiles.ts";
export { MAX_TILE_ATTRIBUTION_LENGTH, TileKind } from "./map/tiles.ts";

export { getStyleUrl, SELECTED_TILE_KEY } from "./map/tile.util.ts";

export {
    createBanner,
    createBannerTranslation,
    deleteBanner,
    deleteBannerTranslation,
    getBanner,
    getBanners,
    getBannerTranslations,
    patchBanner,
    patchBannerTranslation,
    BANNER_NO_EXPIRY_DATE,
} from "./banners/banners.ts";
export type {
    Banner,
    BannerCreate,
    BannerFormat,
    BannerKind,
    BannerPatch,
    BannerTranslation,
    BannerTranslationCreate,
    BannerTranslationPatch,
} from "./banners/banners.ts";

export {
    AccessOperationType,
    getNotifications,
    markNotificationAsRead,
    markAllNotificationsAsRead,
    NotificationSeverityType,
    NotificationStatus,
    NotificationType,
} from "./notifications/notifications.ts";
export type { OpencopNotification } from "./notifications/notifications.ts";

export {
    AUTH_QUERY_KEY,
    cacheUser,
    clearCachedUser,
    clearLastActivity,
    fetchMe,
    globalSignOut,
    getCachedUser,
    updateLastActivity,
    wasRecentlyActive,
} from "./auth/session.ts";

export type { AuthUser } from "./auth/session.ts";

export {
    getPreferences,
    patchPreferences,
    uploadAvatar,
    deleteAvatar,
} from "./auth/preferences.ts";
export type { UserPreferences, UserPreferencesPatch } from "./auth/preferences.ts";

export {
    createMapCapture,
    deleteMapCapture,
    getMapCapture,
    getMapCaptures,
    getSharedMapCapture,
    patchMapCapture,
} from "./map-captures/map-captures.ts";
export type {
    MapCaptureCreate,
    MapCapturePatch,
    MapCaptureResponse,
    MapView,
} from "./map-captures/map-captures.ts";

export { createExport, downloadExport } from "./exports/exports.ts";
export type {
    CreateExportByElementIds,
    CreateExportBySubjects,
    CreateExportRequest,
    ExportResponse,
    ExportSubject,
} from "./exports/exports.ts";

export { isEmptyLayerMergeValidationError, validateMerge, mergeLayers } from "./layers/merge.ts";
export type {
    LayerLifecycleState,
    MergeRequest,
    MergeResponse,
    MergeTargetLayer,
    MergeValidatePairwiseCount,
    MergeValidateRequest,
    MergeValidateResponse,
} from "./layers/merge.ts";
export { prepareAttachmentStream } from "./events/events.ts";
export type { StreamPrepareResponse } from "./events/events.ts";

export { getGeocoders, searchGeocoder } from "./geocoding/geocoding.ts";
export type {
    GeoBoundingBox,
    GeocodedPlace,
    GeocoderSummary,
    GeocodingOperation,
    GeocodingSearchResponse,
    GeoPoint,
    PlaceType,
    SearchGeocoderParams,
} from "./geocoding/geocoding.ts";
