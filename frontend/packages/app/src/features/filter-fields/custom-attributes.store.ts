// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { create } from "zustand";
import { persist } from "zustand/middleware";
import { attributePath, isAttributeName } from "@opencop/contributions/validation";

/**
 * The attributes an operator has added to filter rules — names under
 * `properties.opencop.attributes`. Kept in this browser only; a saved filter that names an
 * attribute this browser never saw still reads back in a summary (`fieldFor` in the
 * provider), but a form cannot save that row until the attribute is added here.
 */
export type CustomAttributesState = {
    /** In the order they were added. */
    names: string[];
};

export type CustomAttributesActions = {
    addAttribute: (name: string) => void;
    removeAttribute: (name: string) => void;
};

export type CustomAttributesStore = CustomAttributesState & { actions: CustomAttributesActions };

/** One saved attribute, in the shape storage has always held. */
type PersistedAttribute = { field: string; label: string; path: string; custom: true };

/** What reaches storage. The shape predates this store; keep it, or saved attributes are lost. */
type PersistedState = { customFields: Record<string, PersistedAttribute> };

export const useCustomAttributesStore = create<CustomAttributesStore>()(
    persist(
        (set) => ({
            names: [],
            actions: {
                addAttribute: (name) =>
                    set((s) => (s.names.includes(name) ? s : { names: [...s.names, name] })),
                removeAttribute: (name) =>
                    set((s) => ({ names: s.names.filter((existing) => existing !== name) })),
            },
        }),
        {
            // Named for the watchbox that first owned the filter; renaming it would drop
            // every operator's saved attributes.
            name: "opencop:watchbox-filter",
            version: 1,
            migrate: (persisted, version) => {
                // v0 blobs stored the whole `fields` map (built-ins included);
                // only the `custom: true` entries are real user data.
                if (
                    version === 0 &&
                    persisted &&
                    typeof persisted === "object" &&
                    "fields" in persisted
                ) {
                    const oldFields =
                        (persisted as { fields?: Record<string, { custom?: boolean }> }).fields ??
                        {};
                    return {
                        customFields: Object.fromEntries(
                            Object.entries(oldFields).filter(([, def]) => def?.custom),
                        ),
                    } as PersistedState;
                }
                return persisted as PersistedState;
            },
            partialize: ({ names }): PersistedState => ({
                customFields: Object.fromEntries(
                    names.map((name) => [
                        name,
                        { field: name, label: name, path: attributePath(name), custom: true },
                    ]),
                ),
            }),
            // Storage is outside our control; a name the backend would refuse never becomes a field.
            merge: (persisted, current) => ({
                ...current,
                names: Object.keys(
                    (persisted as PersistedState | undefined)?.customFields ?? {},
                ).filter(isAttributeName),
            }),
        },
    ),
);
