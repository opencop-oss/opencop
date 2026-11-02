// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { create } from "zustand";
import type { Geometry, Style } from "@opencop/elements";

export interface EventClipboardPayload {
    type: string;
    displayName: string;
    start?: number | null;
    stale: number;
    speed?: number | null;
    course?: number | null;
    heading?: number | null;
    hae?: number;
    code?: string;
    modifiers: Record<string, string>;
    geometry: Geometry;
    style: Style;
}

interface EventClipboardState {
    copiedEvent: EventClipboardPayload | null;
    setCopiedEvent: (payload: EventClipboardPayload) => void;
    clearCopiedEvent: () => void;
}

export const useEventClipboardStore = create<EventClipboardState>((set) => ({
    copiedEvent: null,
    setCopiedEvent: (payload) => set({ copiedEvent: payload }),
    clearCopiedEvent: () => set({ copiedEvent: null }),
}));
