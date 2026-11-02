// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { create } from "zustand";

export type WatchboxPanelStore = {
    selected: string | null;
    hovered: string | null;
    select(id: string | null): void;
    hover(id: string | null): void;
};

export const useWatchboxPanelStore = create<WatchboxPanelStore>()((set) => ({
    selected: null,
    hovered: null,
    select: (id) => set({ selected: id }),
    hover: (id) => set({ hovered: id }),
}));
