// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { producedElementType, type ToolDescriptor } from "@opencop/tools";
import { containTool, type Containment, type ContainmentLog } from "../containment/index.ts";
import { validateTool } from "../validation/index.ts";
import { refuseProblems } from "./refusal.ts";
import { Registry } from "./registry.ts";

export interface ToolRegistryOptions {
    /** The containment every tool's contributed code runs under. */
    readonly containment: Containment;
    /** Where a composition mistake is warned about; omitted, nothing is. */
    readonly log?: Pick<ContainmentLog, "warn">;
}

export interface ToolRegistry {
    /**
     * Add a descriptor; returns a disposer that removes exactly this entry. Refuses a
     * descriptor whose declared product is invalid, or that declares style for a product
     * that is not an element — a composition mistake, caught at registration rather than
     * at the first draw. A second descriptor under a held id is kept, and warned about.
     */
    register(tool: ToolDescriptor): () => void;
    /** Every descriptor, in registration order. A fresh array on each change. */
    readonly getAll: () => readonly ToolDescriptor[];
    /** The first descriptor registered under `id`, as every lookup by id resolves it. */
    get(id: string): ToolDescriptor | undefined;
    readonly subscribe: (listener: () => void) => () => void;
    /** Drop every descriptor: a test seam. */
    clear(): void;
}

/**
 * The registry of toolbar tools, native and plugin alike — the same kind of entry, so
 * the toolbar cannot tell them apart. One instance per host.
 */
export function createToolRegistry({ containment, log }: ToolRegistryOptions): ToolRegistry {
    const registry = new Registry<ToolDescriptor>();
    return {
        register(contributed) {
            refuseProblems("tool-registry", validateTool(contributed));
            const descriptor = containTool(containment, contributed);
            if (log) {
                const all = registry.getAll();
                if (all.some((entry) => entry.id === descriptor.id))
                    log.warn(
                        `[tool-registry]: tool id "${descriptor.id}" registered twice; both remain in the toolbar.`,
                    );
                // Two descriptors producing one element type: a lookup by element type
                // takes the first, so the second subject loses the editor and `onCommit`
                // for elements it believes it owns.
                const elementType = producedElementType(descriptor.produces);
                const claimant = elementType
                    ? all.find((entry) => producedElementType(entry.produces) === elementType)
                    : undefined;
                if (claimant)
                    log.warn(
                        `[tool-registry]: tool "${descriptor.id}" claims element type "${elementType}", already claimed by "${claimant.id}". ` +
                            `Lookups by element type resolve to "${claimant.id}"; the loser still renders a toolbar button.`,
                    );
            }
            registry.add(descriptor);
            return () => registry.remove((entry) => entry === descriptor);
        },
        getAll: registry.getAll,
        get: (id) => registry.getAll().find((descriptor) => descriptor.id === id),
        subscribe: registry.subscribe,
        clear: () => registry.replace([]),
    };
}
