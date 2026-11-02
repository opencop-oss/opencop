// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { PartialOCFeature } from "@opencop/elements";
import { apiFetch, type FetchOptions } from "../client";

/** `author` is absent on objects a data source produced rather than a user. */
export type EventDetails = PartialOCFeature["properties"]["opencop"] & { author?: Contact };

export interface HistoryFeatureCollection {
    type: "FeatureCollection";
    features: HistoryFeature[];
}

export interface HistoryFeature {
    type: "Feature";
    id: string;
    geometry: {
        type: string;
        coordinates: number[] | number[][] | number[][][];
    };
    properties: {
        opencop: OpenCOPProperties;
    };
}

export interface Contact {
    userId: string;
    username?: string;
    firstName: string;
    lastName: string;
    email: string;
    phone: string;
}

export interface OpenCOPProperties {
    id: string;
    element: OpenCOPElement;
    objectId: string;
    version: string;
    displayName: string;
    source: string;
    timestampz: {
        time: number;
        stale: number;
        start: number;
    };
    author?: Contact;
    contacts: Contact[];
    kind: string;
    attributes: Record<string, unknown>;
    extensions: unknown[];
}

export interface OpenCOPElement {
    type: string;
    center?: [number, number];
    fillColor?: string;
    strokeColor?: string;
    strokeStyle?: string;
    strokeWeight?: number;
    speed?: number | null;
    course?: number | null;
    heading?: number | null;
}

export interface Remark {
    id: string;
    objectId: string;
    content: string;
    contact?: Contact | null;
    createdAt: number;
    updatedAt?: number | null;
}

export interface CreateRemarkRequest {
    content: string;
}

export interface UpdateRemarkRequest {
    id: string;
    content: string;
}

export interface BroadcastEventRequest {
    targetLayer: string;
    feature: PartialOCFeature;
}

export interface PatchBroadcastEventRequest {
    targetLayer: string;
    objectId: string;
    feature: PartialOCFeature;
}

export interface DeleteBroadcastEventRequest {
    targetLayer: string;
    objectId: string;
}

export interface PatchSourceBroadcastEventRequest {
    sourceId: string;
    objectId: string;
    feature: PartialOCFeature;
}

export interface DeleteSourceBroadcastEventRequest {
    sourceId: string;
    objectId: string;
}

export async function createBroadcastEvent({
    targetLayer,
    feature,
}: BroadcastEventRequest): Promise<PartialOCFeature | undefined> {
    return apiFetch<PartialOCFeature | undefined>(`/events/broadcast/layer/${targetLayer}`, {
        method: "POST",
        body: JSON.stringify(feature),
    });
}

export async function patchBroadcastEvent({
    targetLayer,
    objectId,
    feature,
}: PatchBroadcastEventRequest): Promise<PartialOCFeature | undefined> {
    return apiFetch<PartialOCFeature | undefined>(
        `/events/broadcast/layer/${targetLayer}/${objectId}`,
        {
            method: "PATCH",
            body: JSON.stringify(feature),
        },
    );
}

export async function deleteBroadcastEvent({
    targetLayer,
    objectId,
}: DeleteBroadcastEventRequest): Promise<unknown> {
    return apiFetch<unknown>(`/events/broadcast/layer/${targetLayer}/${objectId}`, {
        method: "DELETE",
    });
}

export async function patchSourceBroadcastEvent({
    sourceId,
    objectId,
    feature,
}: PatchSourceBroadcastEventRequest): Promise<PartialOCFeature | undefined> {
    return apiFetch<PartialOCFeature | undefined>(
        `/events/broadcast/source/${sourceId}/${objectId}`,
        {
            method: "PATCH",
            body: JSON.stringify(feature),
        },
    );
}

export async function deleteSourceBroadcastEvent({
    sourceId,
    objectId,
}: DeleteSourceBroadcastEventRequest): Promise<unknown> {
    return apiFetch<unknown>(`/events/broadcast/source/${sourceId}/${objectId}`, {
        method: "DELETE",
    });
}

export async function pushEvent(sourceId: string, feature: PartialOCFeature): Promise<unknown> {
    return apiFetch<unknown>(`/connections/${sourceId}/events/push`, {
        method: "POST",
        body: JSON.stringify({
            type: "FeatureCollection",
            features: [feature],
        }),
    });
}

export async function fetchEventDetailsLatest(
    sourceId: string,
    objectId: string,
    options?: FetchOptions,
): Promise<EventDetails> {
    return apiFetch<EventDetails>(`/events/${sourceId}/object/${objectId}/details/latest`, {
        signal: options?.signal,
    });
}

export async function addRemark(
    sourceId: string,
    objectId: string,
    request: CreateRemarkRequest,
): Promise<unknown> {
    return apiFetch(`/events/${sourceId}/${objectId}/remarks`, {
        method: "POST",
        body: JSON.stringify(request),
    });
}

export async function updateRemark(
    sourceId: string,
    objectId: string,
    request: UpdateRemarkRequest,
): Promise<unknown> {
    return apiFetch(`/events/${sourceId}/${objectId}/remarks`, {
        method: "PATCH",
        body: JSON.stringify(request),
    });
}

export async function deleteRemark(
    sourceId: string,
    objectId: string,
    id: string,
): Promise<unknown> {
    return apiFetch(`/events/${sourceId}/${objectId}/remarks/${id}`, {
        method: "DELETE",
    });
}

export async function retrieveRemarks(sourceId: string, objectId: string): Promise<Remark[]> {
    return apiFetch(`/events/${sourceId}/${objectId}/remarks`);
}

export interface SamplingMeta {
    strategy: string;
    maxPointsRequested: number;
    bucketSize: string;
    totalPointsFound: number;
    usedPointsReturned: number;
}

export interface EventHistoryResponse {
    objectId: string;
    from: string;
    to: string;
    sampling: SamplingMeta;
    data: HistoryFeatureCollection;
}

export interface HistoryParams {
    from?: string;
    to?: string;
    maxPoints?: number;
}

export interface ThumbnailRef {
    id: string;
    url: string;
    widthPx: number | null;
    heightPx: number | null;
    mimeType: string | null;
}

export interface MediaImageAttributes {
    type: "IMAGE";
    widthPx: number | null;
    heightPx: number | null;
}

export interface MediaRecord {
    id: string;
    blobKey: string;
    originalFilename: string;
    contentType: "IMAGE" | "VIDEO" | "AUDIO" | "DOCUMENT" | "OTHER";
    sizeBytes: number;
    access: "LOGGED_IN" | "SCOPED";
    creatorId: string;
    ownerId: string;
    createdAt: string;
    mimeType: string | null;
    attributes: MediaImageAttributes | null;
    thumbnailOf: string | null;
    usage: "EVENT_DETAIL" | "AVATAR" | "LAYER_ICON";
}

export interface FileAttachment {
    kind: "FILE";
    record: MediaRecord;
    url: string;
    thumbnails: ThumbnailRef[] | null;
}

/** Transport of a URL attachment; selects which player the carousel constructs. */
export type StreamKind = "RTSP" | "HLS" | "MJPEG" | "MPEGTS" | "SRT";

export interface UrlAttachmentRecord {
    id: string;
    url: string;
    streamKind: StreamKind;
    access: "LOGGED_IN" | "SCOPED";
    /** Null for streams attached by the data source rather than by an operator. */
    creatorId: string | null;
    ownerId: string | null;
    createdAt: string;
    usage: "EVENT_DETAIL" | "AVATAR" | "LAYER_ICON";
}

export interface UrlAttachment {
    kind: "URL";
    record: UrlAttachmentRecord;
}

export type EventAttachment = FileAttachment | UrlAttachment;

export async function fetchEventHistory(
    sourceId: string,
    objectId: string,
    params?: HistoryParams,
    options?: FetchOptions,
): Promise<EventHistoryResponse> {
    const searchParams = new URLSearchParams({ asLine: "false" });
    if (params?.from) searchParams.set("from", params.from);
    if (params?.to) searchParams.set("to", params.to);
    if (params?.maxPoints != null) searchParams.set("maxPoints", String(params.maxPoints));
    return apiFetch<EventHistoryResponse>(
        `/events/${sourceId}/${objectId}/history?${searchParams}`,
        {
            signal: options?.signal,
        },
    );
}

export async function uploadEventAttachment(
    sourceId: string,
    objectId: string,
    original: File,
    thumbnails: File[] = [],
    options?: FetchOptions,
): Promise<FileAttachment> {
    const formData = new FormData();

    formData.append("original", original);

    for (const thumb of thumbnails) {
        formData.append("thumbnails", thumb);
    }

    return apiFetch<FileAttachment>(`/events/${sourceId}/${objectId}/attachments`, {
        method: "POST",
        body: formData,
        signal: options?.signal,
    });
}

export async function registerEventAttachmentUrl(
    sourceId: string,
    objectId: string,
    url: string,
    streamKind: StreamKind,
): Promise<UrlAttachment> {
    return apiFetch<UrlAttachment>(`/events/${sourceId}/${objectId}/attachments`, {
        method: "POST",
        body: JSON.stringify({ url, streamKind }),
    });
}

export async function listEventAttachments(
    sourceId: string,
    objectId: string,
    options?: FetchOptions,
): Promise<EventAttachment[]> {
    return apiFetch<EventAttachment[]>(`/events/${sourceId}/${objectId}/attachments`, {
        signal: options?.signal,
    });
}

export async function deleteEventAttachment(
    sourceId: string,
    objectId: string,
    mediaId: string,
    options?: FetchOptions,
): Promise<void> {
    await apiFetch<void>(`/events/${sourceId}/${objectId}/attachments/${mediaId}`, {
        method: "DELETE",
        signal: options?.signal,
    });
}

export interface StreamPrepareResponse {
    hlsUrl: string;
}

export async function prepareAttachmentStream(
    sourceId: string,
    objectId: string,
    attachmentId: string,
    options?: FetchOptions,
): Promise<StreamPrepareResponse> {
    return apiFetch<StreamPrepareResponse>(
        `/events/${encodeURIComponent(sourceId)}/${encodeURIComponent(objectId)}/attachments/${encodeURIComponent(attachmentId)}/stream/prepare`,
        { method: "POST", signal: options?.signal },
    );
}
