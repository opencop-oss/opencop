// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { create } from "zustand";

/**
 * Toolbar preferences. The interaction session lives in
 * `~/features/map/interaction-runtime/session/interaction.store`; what lives here is per-spec
 * "last used" style persistence, which outlives any session by design.
 */
interface ToolState {
    /**
     * Per-spec "last used" style values. Key is `toolKey:specLabel`.
     * Persists across draw sessions so the next shape starts with the
     * user's last-used color/weight/pattern.
     */
    styleValues: Record<string, unknown>;
}

interface ToolActions {
    setStyleValue(key: string, value: unknown): void;
}

export type ToolStore = ToolState & ToolActions;

/** Build the store key for a style value: `toolKey:specLabel`. */
export function styleKey(toolKey: string, specLabel: string): string {
    return `${toolKey}:${specLabel}`;
}

export const useToolStore = create<ToolStore>((set) => ({
    styleValues: {},
    setStyleValue: (key, value) =>
        set((s) => ({ styleValues: { ...s.styleValues, [key]: value } })),
}));
