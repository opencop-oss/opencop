// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * The PICK TARGET — what was under the pointer, as a pick response receives it.
 * Opaque on purpose: a response is handed the id the nearest non-element hit
 * answers to and the object it was, plus the elements beneath, and nothing of
 * deck's `PickingInfo` — not the layer, not the sublayer, not the pixel.
 *
 * Identity comes from the scene: every primitive is drawn for one object of one
 * store, and a pick decodes back to that object's {@link PickedRef}. A hit
 * answers to its store's id, and the response registered under that id receives
 * the ref. A pickable layer outside the scene answers with no id, so a click on
 * it is not dispatched to a response.
 */
import type { Element } from "@opencop/elements";

/** The object a scene primitive was drawn for: its store's id, and its key in that store. */
export interface PickedRef {
    readonly type: string;
    readonly key: string;
}

/**
 * Something under the pointer that is not an element: the id of the store whose
 * object it was drawn for, and that object's ref — or neither, for a pickable
 * layer outside the scene.
 */
export type Hit =
    | { readonly responseId: string; readonly ref: PickedRef }
    | { readonly responseId: null; readonly ref: null };

export interface PickTarget {
    /** Canvas pixels of the click. */
    readonly screen: { readonly x: number; readonly y: number };
    /**
     * The nearest thing under the pointer that is not an element, or null over
     * empty ground, and null when the nearest thing is an element. A hit with a
     * null `responseId` is still a hit: ambient uses its presence to look for an
     * element beneath it.
     */
    readonly hit: Hit | null;
    /** Every element under the pointer, nearest first. */
    readonly elements: readonly Element[];
}

/** A pick target whose nearest hit was drawn for a store's object — what a pick response receives. */
export type StorePickTarget = PickTarget & {
    readonly hit: { readonly responseId: string; readonly ref: PickedRef };
};

/**
 * What a click on a store's objects does. A response is registered under its
 * store's id, so it only ever receives refs to that store's objects.
 */
export interface PickResponse {
    /** A primary click landed on an object of the store registered under this response's id. */
    onClick(target: StorePickTarget): void;
}
