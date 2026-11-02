// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { PickResponse } from "../pick-response.ts";
import type { ContainmentLog } from "../containment/index.ts";

export interface PickResponseRegistry {
    /**
     * Register the response for an id; returns the disposer that withdraws it. A second
     * registration for an occupied id replaces the first, and is warned about — two
     * features answering for one id is a composition mistake; a stale disposer cannot
     * evict the response that replaced it. The response arrives contained.
     */
    register(id: string, response: PickResponse): () => void;
    /** The response registered for `id`, if any. */
    get(id: string): PickResponse | undefined;
    /** Every registration, by id, as it is now. */
    entries(): readonly (readonly [string, PickResponse])[];
}

/**
 * The registry of pick responses, by the id a scene primitive's store answers with. The
 * interaction runtime dispatches a click to it; the contribution context contains each
 * response before it arrives. One instance per host.
 */
export function createPickResponseRegistry({
    log,
}: {
    readonly log?: Pick<ContainmentLog, "warn">;
} = {}): PickResponseRegistry {
    const responses = new Map<string, PickResponse>();
    return {
        register(id, response) {
            if (responses.has(id))
                log?.warn(
                    `pick response "${id}" re-registered; the previous response is replaced.`,
                );
            responses.set(id, response);
            return () => {
                if (responses.get(id) === response) responses.delete(id);
            };
        },
        get: (id) => responses.get(id),
        entries: () => [...responses],
    };
}
