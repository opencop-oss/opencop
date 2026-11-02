// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { Element } from "@opencop/elements";
import type { ElementMapper } from "../element-mapper.ts";
import {
    containElementMapper,
    type Containment,
    type ContributionOwner,
} from "../containment/index.ts";
import { validateElementMapper } from "../validation/index.ts";
import { refuseProblems } from "./refusal.ts";
import { Registry } from "./registry.ts";

/** A registration, with its owner and its mapper contained. */
export interface ElementMapperEntry extends ContributionOwner {
    readonly mapper: ElementMapper<Element, unknown>;
}

export interface ElementMapperRegistry {
    /**
     * Register how `mapper.type` is drawn, owned by `owner`. Refuses a malformed mapper,
     * and a type another registration holds.
     */
    register<E extends Element, S>(
        owner: ContributionOwner,
        mapper: ElementMapper<E, S>,
    ): () => void;
    /** Every registration, with its owner. A fresh array on each change. */
    readonly getEntries: () => readonly ElementMapperEntry[];
    readonly subscribe: (listener: () => void) => () => void;
}

/**
 * The registry of element mappers: how each element type is drawn in the scene. One
 * mapper per type; a type another owner holds is refused. Each mapper is checked against
 * the shared rules and contained once, at registration — which also hands its selector
 * only the state a mapper may read. One instance per host.
 */
export function createElementMapperRegistry({
    containment,
}: {
    readonly containment: Containment;
}): ElementMapperRegistry {
    const registry = new Registry<ElementMapperEntry>();
    return {
        register(owner, mapper) {
            // Sound to widen: the scene pipeline hands a mapper only elements of its type.
            const widened = mapper as unknown as ElementMapper<Element, unknown>;
            refuseProblems("element-mapper", validateElementMapper(widened));
            const holder = registry.getAll().find((entry) => entry.mapper.type === mapper.type);
            if (holder)
                throw new Error(
                    `[element-mapper]: refused: "${mapper.type}" is already held by "${holder.ownerId}".`,
                );
            const entry: ElementMapperEntry = {
                ...owner,
                mapper: containElementMapper(containment, owner, widened),
            };
            registry.add(entry);
            return () => {
                registry.remove((candidate) => candidate === entry);
                containment.rearmContributions(`element-mapper/${mapper.type}`);
            };
        },
        getEntries: registry.getAll,
        subscribe: registry.subscribe,
    };
}
