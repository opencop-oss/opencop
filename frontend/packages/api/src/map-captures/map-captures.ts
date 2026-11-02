// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { apiFetch, type FetchOptions } from "../client";

export interface MapView {
    bearing: number;
    latitude: number;
    longitude: number;
    pitch: number;
    zoom: number;
}

export interface MapCaptureResponse {
    id: string;
    name: string;
    mapView: MapView;
    createdAt: string;
    updatedAt: string;
}

export interface MapCaptureCreate {
    name: string;
    mapView: MapView;
}

export interface MapCapturePatch {
    name?: string;
    mapView?: Partial<MapView>;
}

const BASE = "/me/map/captures";

export async function getMapCaptures(options?: FetchOptions): Promise<MapCaptureResponse[]> {
    return apiFetch<MapCaptureResponse[]>(BASE, { signal: options?.signal });
}

export async function getMapCapture(
    captureId: string,
    options?: FetchOptions,
): Promise<MapCaptureResponse> {
    return apiFetch<MapCaptureResponse>(`${BASE}/${captureId}`, { signal: options?.signal });
}

export async function createMapCapture(data: MapCaptureCreate): Promise<MapCaptureResponse> {
    return apiFetch<MapCaptureResponse>(BASE, { method: "POST", body: JSON.stringify(data) });
}

export async function patchMapCapture(
    captureId: string,
    data: MapCapturePatch,
): Promise<MapCaptureResponse> {
    return apiFetch<MapCaptureResponse>(`${BASE}/${captureId}`, {
        method: "PATCH",
        body: JSON.stringify(data),
    });
}

export async function deleteMapCapture(captureId: string): Promise<void> {
    return apiFetch<void>(`${BASE}/${captureId}`, { method: "DELETE" });
}

export async function getSharedMapCapture(
    captureId: string,
    options?: FetchOptions,
): Promise<MapCaptureResponse> {
    return apiFetch<MapCaptureResponse>(`/map/captures/${captureId}`, {
        signal: options?.signal,
    });
}
