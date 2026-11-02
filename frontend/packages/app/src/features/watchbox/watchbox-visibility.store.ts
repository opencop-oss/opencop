// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { create } from "zustand";

export type WatchboxVisibilityState = {
    hideDisabled: boolean;
};

export type WatchboxVisibilityActions = {
    setHideDisabled: (hideDisabled: boolean) => void;
};

export type WatchboxVisibilityStore = WatchboxVisibilityState & {
    actions: WatchboxVisibilityActions;
};

// Deliberately not persisted: unlike a hidden layer/source (a named thing the user chose to
// hide), this filters on status. Surviving reload would let a watchbox that becomes DISABLED
// later silently vanish from view with no cue that a filter, not a deletion, is responsible —
// the opposite of what a degraded-monitoring-state feature should do.
export const useWatchboxVisibilityStore = create<WatchboxVisibilityStore>()((set) => ({
    hideDisabled: false,
    actions: {
        setHideDisabled: (hideDisabled) => set({ hideDisabled }),
    },
}));
