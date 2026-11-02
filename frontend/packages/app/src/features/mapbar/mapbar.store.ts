// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { create } from "zustand";

interface MapbarStore {
    /** Explicit user panel choice (toggle/open/close). Generic across all panels. */
    sidebarPanel: string;
    togglePanel: (tab: string) => void;
    openPanel: (tab: string) => void;
    closePanel: () => void;
}

export const useMapbarStore = create<MapbarStore>((set) => ({
    sidebarPanel: "",

    togglePanel: (tab) => set((s) => ({ sidebarPanel: s.sidebarPanel === tab ? "" : tab })),

    openPanel: (tab) => set({ sidebarPanel: tab }),

    closePanel: () => set({ sidebarPanel: "" }),
}));
