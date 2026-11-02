// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { apiFetch, type FetchOptions } from "../client";
import type { RecordQueryOptions } from "../common";
import type { FramingMode } from "./integrations";

export interface KafkaCredentials {
    topicName: string;
    username: string;
    password: string;
    bootstrapServer: string;
}

export type AccessMode = "R" | "W" | "RW";
export type TransportType = "TCP" | "UDP" | "KAFKA" | "RTSP";

/** Connection lifecycle states returned by the Connect API. */
export type ConnectionState =
    | "NEW"
    | "CONNECTING"
    | "CONNECTED"
    | "DISCONNECTED"
    | "STOPPED"
    | "FAILED"
    | (string & {}); // Allows other strings but preserves autocomplete

interface ConnectionResponseFields {
    id: string;
    name: string;
    state: ConnectionState;
    access: AccessMode;
    transportType: TransportType;
    defaultVisible: boolean;
}

export type DataSourceRole = "OWNER" | "ADMIN" | "VIEWER";

export const DataSourceRoles = {
    OWNER: "OWNER",
    ADMIN: "ADMIN",
    VIEWER: "VIEWER",
} as const satisfies Record<string, DataSourceRole>;

/**
 * Response item from GET /v1/sources/{id}/users
 * Maps to Java's SourceUser DTO.
 */
export interface DataSourceUser {
    firstName: string;
    lastName: string;
    username: string;
    serverId: string;
    userId: string;
    role: DataSourceRole;
    grantedAt: string;
}

/** Represents a layer assigned to a data source */
export interface DataSourceLayer {
    serverName: string | null;
    serverId: string;
    serverState: string | null;
    role: "OWNER" | "EDITOR" | "VIEWER" | string;
    layer: {
        id: string;
        name: string;
        createdAt: string;
    };
    grantedAt: string;
}

/**
 * Why a connection's `state` is `FAILED`, when the runtime reported a bounded reason.
 * `INTEGRATION_UNAVAILABLE`: the Connect instance owning it registers no implementation for the
 * integration schema version it references, so it is not started.
 */
export type ConnectionStateReason = "INTEGRATION_UNAVAILABLE" | (string & {});

/** The `stateReason` Core reports when a connection's integration has no implementation to run it. */
export const INTEGRATION_UNAVAILABLE = "INTEGRATION_UNAVAILABLE" satisfies ConnectionStateReason;

/** The integration schema version a connection is bound to. */
export interface ConnectionIntegrationRef {
    integrationId: string;
    schemaVersion: number;
}

export interface DataSourceInfo extends ConnectionResponseFields {
    /** The integration the connection is bound to; kept as-is even when it is unavailable. */
    integration?: ConnectionIntegrationRef | null;
    stateReason?: ConnectionStateReason | null;
    securityType?: string;
    createdAt?: string;
    createdBy?: string | null;
    lastChange?: string;
    /** Last time a message was received on this connection (null if never or not in memory) */
    lastMessageAt?: string | null;
    updatedAt?: string | null;
    updatedBy?: string | null;
    lastEventAt?: string | null;
    /** The mapper, framing and integration the connection runs with. */
    setup?: ConnectionSetup | null;
    archivedAt?: string | null;
    archivedBy?: string | null;
}

export interface SourceCandidate {
    id: string;
    name: string;
}

export interface MyDataSource {
    role: DataSourceRole | string;
    directRole: DataSourceRole | null;
    serverId: string;
    serverName: string;
    serverState: ConnectionState | string;
    via: "DIRECT" | "INDIRECT" | "BOTH" | string;
    access: AccessMode;
    transportType: TransportType;
    defaultVisible: boolean;
    createdAt?: string | null;
    createdBy?: string | null;
    updatedAt?: string | null;
    updatedBy?: string | null;
    /** Latest recorded message receipt; null when none has been recorded. */
    lastEventAt?: string | null;
    archivedAt?: string | null;
    archivedBy?: string | null;
}

const CONNECTIONS = "/connections";
const SOURCES = "/sources";
const ME_SOURCES = "/me/sources";

export async function getDataSources(options?: RecordQueryOptions): Promise<DataSourceInfo[]> {
    const params = options?.recordVisibility ? `?recordVisibility=${options.recordVisibility}` : "";
    return apiFetch<DataSourceInfo[]>(`/admin/connections${params}`, { signal: options?.signal });
}

export async function getDataSource(id: string, options?: FetchOptions): Promise<DataSourceInfo> {
    return apiFetch<DataSourceInfo>(`${CONNECTIONS}/${id}`, { signal: options?.signal });
}

export async function getSourceCandidatesForLayer(
    layerId: string,
    options?: FetchOptions,
): Promise<SourceCandidate[]> {
    return apiFetch<SourceCandidate[]>(`/layers/${layerId}/source-candidates`, {
        signal: options?.signal,
    });
}

export async function getMyDataSources(options?: RecordQueryOptions): Promise<MyDataSource[]> {
    const params = options?.recordVisibility ? `?recordVisibility=${options.recordVisibility}` : "";
    return apiFetch<MyDataSource[]>(`${ME_SOURCES}${params}`, { signal: options?.signal });
}

export async function getLayersForDataSource(
    serverId: string,
    options?: FetchOptions,
): Promise<DataSourceLayer[]> {
    return apiFetch<DataSourceLayer[]>(`${SOURCES}/${serverId}/layers`, {
        signal: options?.signal,
    });
}

export async function getUsersForDataSource(
    serverId: string,
    options?: FetchOptions,
): Promise<DataSourceUser[]> {
    return apiFetch<DataSourceUser[]>(`${SOURCES}/${serverId}/users`, { signal: options?.signal });
}

export async function addLayersToDataSource(
    serverId: string,
    layerIds: string[],
    role: "VIEWER" | "ADMIN" | "OWNER" = "OWNER",
): Promise<void> {
    const body: { viewers: string[]; admins: string[]; owners: string[] } = {
        viewers: [],
        admins: [],
        owners: [],
    };
    switch (role) {
        case "VIEWER":
            body.viewers = layerIds;
            break;
        case "ADMIN":
            body.admins = layerIds;
            break;
        case "OWNER":
            body.owners = layerIds;
            break;
    }
    await apiFetch<void>(`${SOURCES}/${serverId}/layers`, {
        method: "POST",
        body: JSON.stringify(body),
    });
}

/** How a v2 TCP connection splits its stream: a delimiter it supplies, or an integration framing. */
export type TcpFramingSelection =
    | {
          type: "CUSTOM";
          mode: FramingMode;
          /** Required by `delimiterInclude` and `delimiterSplit`; rejected by the other modes. */
          delimiter?: string;
      }
    | { type: "INTEGRATION"; framingId: string };

interface ConnectionSetupFields {
    integration: { id: string; schemaVersion: number } | null;
    /** Null, with `values` empty, for a connection whose setup was never captured (v1-created). */
    extractorId: string | null;
    values: Record<string, string>;
    /** The ingress mapper the connection decodes with; null for a TCP connection that only writes. */
    mapperId: string | null;
}

/** A connection's setup as Core returns it, in the shape of its v2 create request. */
export type ConnectionSetup =
    | (ConnectionSetupFields & {
          kind: "TCP";
          egressMapperId: string | null;
          framing: TcpFramingSelection | null;
      })
    | (ConnectionSetupFields & { kind: "UDP" | "RTSP" | "KAFKA" });

/**
 * POST /v2/connections body for a TCP connection. `values` carries the selected extractor's
 * declared TEXT and SECRET fields, all as text; FILE fields travel as multipart parts instead.
 * `mapperId` (ingress) is sent for R and RW, `egressMapperId` for W and RW; framing is always sent.
 */
export interface CreateTcpConnectionRequest {
    kind: "TCP";
    name: string;
    access: AccessMode;
    integration: { id: string; schemaVersion: number };
    extractorId: string;
    values: Record<string, string>;
    mapperId?: string;
    egressMapperId?: string;
    framing: TcpFramingSelection;
}

/**
 * POST /v2/connections body for a UDP, RTSP or Kafka connection: the extractor's declared values
 * and the ingress mapper. These kinds only read, and carry no framing or egress mapper.
 */
export interface CreateMapperConnectionRequest {
    kind: "UDP" | "RTSP" | "KAFKA";
    name: string;
    access: AccessMode;
    integration: { id: string; schemaVersion: number };
    extractorId: string;
    values: Record<string, string>;
    mapperId: string;
}

export type CreateConnectionV2Request = CreateTcpConnectionRequest | CreateMapperConnectionRequest;

/** 201 response from POST /v2/connections: Core's flat connection record, tagged with its kind. */
export interface ConnectionCreatedV2Response extends ConnectionResponseFields {
    kind: TransportType;
    /** Kafka only: the producer's credentials, returned in this response and never again. */
    kafkaCredentials?: KafkaCredentials;
}

/**
 * POST /v2/connections. Sent as JSON when no files are attached; otherwise multipart, with the JSON
 * in a `body` part and one part per file under its declared field name (repeated for FILE_LIST).
 */
export async function createConnectionV2(
    request: CreateConnectionV2Request,
    files: Record<string, File[]> = {},
): Promise<ConnectionCreatedV2Response> {
    const fileParts = Object.entries(files).filter(([, list]) => list.length > 0);
    if (fileParts.length === 0) {
        return apiFetch<ConnectionCreatedV2Response>(CONNECTIONS, {
            apiVersion: "/v2",
            method: "POST",
            body: JSON.stringify(request),
        });
    }
    const formData = new FormData();
    formData.append("body", new Blob([JSON.stringify(request)], { type: "application/json" }));
    for (const [name, list] of fileParts) {
        for (const file of list) formData.append(name, file, file.name);
    }
    return apiFetch<ConnectionCreatedV2Response>(CONNECTIONS, {
        apiVersion: "/v2",
        method: "POST",
        body: formData,
    });
}

export async function addUsersToDataSource(
    serverId: string,
    userId: string[],
    role: "VIEWER" | "ADMIN" | "OWNER",
): Promise<void> {
    const body: { viewers: string[]; admins: string[]; owners: string[] } = {
        viewers: [],
        admins: [],
        owners: [],
    };
    switch (role) {
        case "VIEWER":
            body.viewers = userId;
            break;
        case "ADMIN":
            body.admins = userId;
            break;
        case "OWNER":
            body.owners = userId;
            break;
        default:
            throw new Error("Invalid role selected");
    }

    await apiFetch<void>(`${SOURCES}/${serverId}/users`, {
        method: "POST",
        body: JSON.stringify(body),
    });
}

export async function deleteDataSource(serverId: string) {
    await apiFetch<void>(`${CONNECTIONS}/${serverId}`, { method: "DELETE" });
}

export async function deleteLayerFromDataSource(serverId: string, layerId: string): Promise<void> {
    await apiFetch<void>(`${SOURCES}/${serverId}/layers`, {
        method: "DELETE",
        body: JSON.stringify([layerId]),
    });
}

export async function deleteUserFromDataSource(serverId: string, userId: string): Promise<void> {
    await apiFetch<void>(`${SOURCES}/${serverId}/users`, {
        method: "DELETE",
        body: JSON.stringify([userId]),
    });
}

export interface DataSourcePatch {
    name?: string;
    access?: AccessMode;
    defaultVisible?: boolean;
}

export async function patchDataSource(id: string, data: DataSourcePatch): Promise<void> {
    await apiFetch(`${CONNECTIONS}/${id}`, {
        method: "PATCH",
        body: JSON.stringify(data),
        ignoreSuccessfulResponseBody: true,
    });
}

export async function disconnectDataSource(serverId: string) {
    await apiFetch<void>(`${CONNECTIONS}/${serverId}/stop`, {
        method: "POST",
        body: JSON.stringify({}),
    });
}

export async function reconnectDataSource(serverId: string) {
    await apiFetch<void>(`${CONNECTIONS}/${serverId}/start`, {
        method: "POST",
        body: JSON.stringify({}),
    });
}
