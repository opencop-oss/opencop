// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { create } from "zustand";

export interface ImageryOverlayEntry {
    id: string;
    opacity: number;
    /** ISO 8601 UTC timestamp for WMS TIME parameter. null = no time filter. */
    time: string | null;
    visible: boolean;
}

interface ImageryOverlayState {
    /** Ordered list of active overlay tile IDs + opacity. Index 0 = visually on top.
     *  Intentionally not persisted — overlays are a transient per-session UI choice
     *  and server-side tile availability can change between sessions. */
    overlays: ImageryOverlayEntry[];
}

interface ImageryOverlayActions {
    toggleOverlay(id: string): void;
    setOverlayOpacity(id: string, opacity: number): void;
    setOverlayTime(id: string, time: string | null): void;
    setOverlayVisible(id: string, visible: boolean): void;
    /** Remove entries for tile IDs that no longer exist on the server. */
    reconcile(tileIds: string[]): void;
    /** Reorder active overlays to match the given candidate ID order (inactive IDs are ignored). */
    reorderOverlays(ids: string[]): void;
}

type ImageryOverlayStore = ImageryOverlayState & { actions: ImageryOverlayActions };

const DEFAULT_OVERLAY_OPACITY = 50;

export const useImageryOverlayStore = create<ImageryOverlayStore>()((set) => ({
    overlays: [],
    actions: {
        toggleOverlay(id) {
            set((prev) => {
                const isActive = prev.overlays.some((o) => o.id === id);
                if (isActive) {
                    return { overlays: prev.overlays.filter((o) => o.id !== id) };
                }
                return {
                    overlays: [
                        ...prev.overlays,
                        { id, opacity: DEFAULT_OVERLAY_OPACITY, time: null, visible: true },
                    ],
                };
            });
        },
        setOverlayVisible(id, visible) {
            set((prev) => ({
                overlays: prev.overlays.map((o) => (o.id === id ? { ...o, visible } : o)),
            }));
        },
        setOverlayOpacity(id, opacity) {
            set((prev) => ({
                overlays: prev.overlays.map((o) => (o.id === id ? { ...o, opacity } : o)),
            }));
        },
        setOverlayTime(id, time) {
            set((prev) => ({
                overlays: prev.overlays.map((o) => (o.id === id ? { ...o, time } : o)),
            }));
        },
        reconcile(tileIds) {
            set((prev) => {
                const validIds = new Set(tileIds);
                const next = prev.overlays.filter((o) => validIds.has(o.id));
                if (next.length === prev.overlays.length) return {};
                return { overlays: next };
            });
        },
        reorderOverlays(ids) {
            set((prev) => {
                if (prev.overlays.length === 0) return {};
                const overlayById = new Map(prev.overlays.map((o) => [o.id, o]));
                const next = ids.flatMap((id) => {
                    const o = overlayById.get(id);
                    return o ? [o] : [];
                });
                if (next.every((o, i) => o.id === prev.overlays[i]?.id)) return {};
                return { overlays: next };
            });
        },
    },
}));

export function useImageryOverlayActions(): ImageryOverlayActions {
    return useImageryOverlayStore((s) => s.actions);
}
