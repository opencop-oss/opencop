// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

export interface ElementIdentity {
    source?: string;
    objectId: string;
}

export interface ElementRef {
    sourceId: string;
    objectId: string;
}

/** Sentinel value indicating the element was created locally. */
export const localEventSource = "local";

/**
 * Internal element keys stay compact and opaque to callers.
 * We use a NUL separator because URL-facing code now serializes its own
 * human-readable `source/objectId` form, so this key no longer needs to be
 * externally legible.
 */
const KEY_SEPARATOR = "\0";

type ElementRefLike = ElementIdentity | ElementRef;

export function toElementRef(identity: ElementRefLike): ElementRef {
    return {
        sourceId:
            "sourceId" in identity ? identity.sourceId : (identity.source ?? localEventSource),
        objectId: identity.objectId,
    };
}

export function getElementKey(identity: ElementRefLike): string {
    const { sourceId, objectId } = toElementRef(identity);
    return `${sourceId}${KEY_SEPARATOR}${objectId}`;
}

/**
 * Key for historical elements — uses the per-version `id` UUID instead of
 * `objectId` so every chunk's version of an entity coexists in the store
 * (each version has a unique key). The stable `objectId` stays untouched
 * and can still be used for downstream concerns like pick resolution.
 */
export function getElementHistoricalKey(identity: { source?: string; id: string }): string {
    const sourceId = identity.source ?? localEventSource;
    return `${sourceId}${KEY_SEPARATOR}${identity.id}`;
}

export function elementRefToKey(ref: ElementRef): string {
    return getElementKey(ref);
}

export function parseElementKey(key: string): ElementRef | null {
    const separatorIndex = key.indexOf(KEY_SEPARATOR);
    if (separatorIndex >= 0) {
        return {
            sourceId: key.slice(0, separatorIndex),
            objectId: key.slice(separatorIndex + KEY_SEPARATOR.length),
        };
    }

    return null;
}

export function elementKeyToRef(key: string): ElementRef | null {
    return parseElementKey(key);
}

export function elementRefsEqual(a: ElementRef | null, b: ElementRef | null): boolean {
    return a?.sourceId === b?.sourceId && a?.objectId === b?.objectId;
}
