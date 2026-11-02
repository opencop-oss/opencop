// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { Layer } from "@deck.gl/core";

/**
 * Layer render priority constants for `useDeckLayers()`.
 * Lower number = rendered first = visually underneath.
 *
 * Overlay outlines (hover, selection) render ABOVE data layers as offset
 * rings with transparent fill, so they don't occlude the element itself.
 */
export const DECK_PRIORITY_TERRAIN = 0;
export const DECK_PRIORITY_IMAGERY_OVERLAY = 4;
export const DECK_PRIORITY_WEATHER = 5;
/** The scene: every element, watchbox and session product. */
export const DECK_PRIORITY_SCENE = 10;
export const DECK_PRIORITY_SELECTION_BOUNDARY = 25;
/** The edit handles, above everything they adjust so a grab target is never covered. */
export const DECK_PRIORITY_EDIT_HANDLES = 30;
export const DECK_PRIORITY_TRIP = 40;

/**
 * Registers a named set of deck.gl layers at a given priority.
 * Calling with an empty array removes the registration.
 */
export type RegisterLayers = (
    id: string,
    layers: (Layer | null | undefined)[],
    priority: number,
) => void;
