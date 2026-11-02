// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { create } from "zustand";
import { persist } from "zustand/middleware";

type NotificationPreferencesStore = {
    showImmediately: boolean;
    actions: {
        toggleShowImmediately: () => void;
    };
};

export const useNotificationPreferencesStore = create<NotificationPreferencesStore>()(
    persist(
        (set) => ({
            showImmediately: false,
            actions: {
                toggleShowImmediately: () => set((s) => ({ showImmediately: !s.showImmediately })),
            },
        }),
        {
            name: "opencop:notification-preferences",
            partialize: (s) => ({ showImmediately: s.showImmediately }),
        },
    ),
);
