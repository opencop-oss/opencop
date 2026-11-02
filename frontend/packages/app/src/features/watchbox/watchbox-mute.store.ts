// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { create } from "zustand";
import { persist } from "zustand/middleware";

type WatchboxMuteState = {
    mutedIdsByUser: Record<string, string[]>;
};

type WatchboxMuteActions = {
    setMuted: (userId: string, watchboxId: string, muted: boolean) => void;
    isMuted: (userId: string, watchboxId: string) => boolean;
};

type WatchboxMuteStore = WatchboxMuteState & {
    actions: WatchboxMuteActions;
};

export const WATCHBOX_MUTE_STORAGE_KEY = "opencop:watchbox-mutes";

/**
 * Stable reference for "this user has no mutes" so a selector falling back to
 * it doesn't create a new array identity on every read — a fresh `[]` literal
 * here would defeat Zustand's change detection for subscribers.
 */
export const EMPTY_MUTED_IDS: readonly string[] = [];

/**
 * Accepts only an object of string arrays; drops non-array entries, non-string
 * ids, and duplicates. Falls back to `{}` for anything else (e.g. a corrupted
 * or pre-migration blob), so a malformed persisted value can never crash
 * hydration or leak into `isMuted`/`setMuted`.
 */
function normalizeMutedIdsByUser(value: unknown): Record<string, string[]> {
    if (typeof value !== "object" || value === null) return {};

    const normalized: Record<string, string[]> = {};
    for (const [userId, ids] of Object.entries(value as Record<string, unknown>)) {
        if (!Array.isArray(ids)) continue;
        const unique = [...new Set(ids.filter((id): id is string => typeof id === "string"))];
        if (unique.length > 0) normalized[userId] = unique;
    }
    return normalized;
}

/**
 * Per-watchbox alert-toast mute preference, partitioned by user id so it
 * isolates analysts sharing a workstation.
 *
 * Departs from docs/best-practices/zustand/index.md's "do not scope by user
 * ID" persistence guidance: that rule's own rationale ("localStorage is
 * cleared on logout") doesn't hold here — AuthContext.tsx's logout() clears
 * only cachedUser, lastActivity, and sessionStorage, never general feature
 * localStorage keys — so a flat key would leak one analyst's mutes to the
 * next on a shared workstation.
 */
export const useWatchboxMuteStore = create<WatchboxMuteStore>()(
    persist(
        (set, get) => ({
            mutedIdsByUser: {},
            actions: {
                setMuted: (userId, watchboxId, muted) =>
                    set((state) => {
                        const current = state.mutedIdsByUser[userId] ?? EMPTY_MUTED_IDS;
                        const isCurrentlyMuted = current.includes(watchboxId);
                        if (muted === isCurrentlyMuted) return state;

                        const mutedIdsByUser = { ...state.mutedIdsByUser };
                        const next = muted
                            ? [...current, watchboxId]
                            : current.filter((id) => id !== watchboxId);
                        if (next.length > 0) mutedIdsByUser[userId] = next;
                        else delete mutedIdsByUser[userId];
                        return { mutedIdsByUser };
                    }),
                isMuted: (userId, watchboxId) =>
                    (get().mutedIdsByUser[userId] ?? EMPTY_MUTED_IDS).includes(watchboxId),
            },
        }),
        {
            name: WATCHBOX_MUTE_STORAGE_KEY,
            partialize: (state) => ({ mutedIdsByUser: state.mutedIdsByUser }),
            merge: (persisted, current) => ({
                ...current,
                mutedIdsByUser: normalizeMutedIdsByUser(
                    (persisted as Partial<WatchboxMuteState> | null | undefined)?.mutedIdsByUser,
                ),
            }),
        },
    ),
);
