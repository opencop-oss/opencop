// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { FilterField } from "@opencop/ui";
import {
    containFilterField,
    type Containment,
    type ContributionOwner,
} from "../containment/index.ts";
import { validateFilterField, type ValidationEnvironment } from "../validation/index.ts";
import { refuseProblems } from "./refusal.ts";
import { Registry } from "./registry.ts";

export interface FilterFieldRegistryOptions {
    /** The containment every field's contributed code runs under. */
    readonly containment: Containment;
    /** The host facts the field rules need: whether a label key resolves. */
    readonly environment: ValidationEnvironment;
}

/** A registration, with its owner and its contributed code contained. */
export interface FilterFieldEntry extends ContributionOwner {
    readonly field: FilterField;
}

export interface FilterFieldRegistry {
    /** Contribute a field, owned by `owner`. Refuses a malformed field or a taken id. */
    register(owner: ContributionOwner, field: FilterField): () => void;
    /** Every registration, a fresh array on each change — a `useSyncExternalStore` snapshot. */
    readonly getEntries: () => readonly FilterFieldEntry[];
    readonly subscribe: (listener: () => void) => () => void;
}

/**
 * The registry of contributed filter fields: what filter rules can be about, beyond
 * the host's own fields. Each field is checked against the shared rules and its
 * contributed code contained once, at registration, so a filter builder holds only safe
 * callables. One instance per host.
 */
export function createFilterFieldRegistry({
    containment,
    environment,
}: FilterFieldRegistryOptions): FilterFieldRegistry {
    const registry = new Registry<FilterFieldEntry>();
    return {
        register(owner, field) {
            refuseProblems("filter-field", validateFilterField(field, environment));
            const holder = registry.getAll().find((entry) => entry.field.id === field.id);
            if (holder)
                throw new Error(
                    `[filter-field]: refused: "${field.id}" is already held by "${holder.ownerId}".`,
                );
            const entry: FilterFieldEntry = {
                field: containFilterField(containment, owner, field),
                ...owner,
            };
            registry.add(entry);
            return () => registry.remove((candidate) => candidate === entry);
        },
        getEntries: registry.getAll,
        subscribe: registry.subscribe,
    };
}
