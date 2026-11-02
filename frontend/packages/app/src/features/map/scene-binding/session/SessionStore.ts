// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ObjectRef, Store, StoreEvent } from "@opencop/ingest";
import { SESSION_STORE_ID, type ToolDescriptor } from "@opencop/tools";
import {
    getInteractionState,
    subscribeInteraction,
} from "../../interaction-runtime/session/interaction.store";
import { sessionConstructionPath, sessionProduct, type SessionProduct } from "./session-render";

/** What the running session draws: its product when not an element, and its construction path. */
export interface SessionObject {
    readonly key: "current";
    readonly product: SessionProduct | null;
    readonly construction: number[] | null;
}

/** The running session as a store of at most one object. */
export interface SessionStore extends Store<SessionObject> {
    /** Start following the interaction session; returns the stop. */
    connect(): () => void;
}

type Listener = (objects: SessionObject[]) => void;

export function createSessionStore(
    resolveTool: (descriptorId: string) => ToolDescriptor | undefined,
): SessionStore {
    let current: SessionObject | null = null;
    const listeners: Record<StoreEvent, Set<Listener>> = {
        add: new Set(),
        update: new Set(),
        remove: new Set(),
    };
    const emit = (event: StoreEvent, object: SessionObject) =>
        listeners[event].forEach((listener) => listener([object]));

    // The draft is mutated in place, so a present object is always re-emitted.
    const sync = () => {
        const { session } = getInteractionState();
        const product = sessionProduct(session, resolveTool);
        const construction = sessionConstructionPath(session, resolveTool);
        const next: SessionObject | null =
            product || construction ? { key: "current", product, construction } : null;

        const prev = current;
        current = next;
        if (prev && !next) emit("remove", prev);
        else if (!prev && next) emit("add", next);
        else if (next) emit("update", next);
    };

    return {
        type: SESSION_STORE_ID,
        get: (key) => (current?.key === key ? current : undefined),
        getAll: () => (current ? [current] : []),
        ref: (object): ObjectRef => ({ type: SESSION_STORE_ID, key: object.key }),
        on: (event, listener) => void listeners[event].add(listener),
        off: (event, listener) => void listeners[event].delete(listener),
        connect() {
            sync();
            return subscribeInteraction((state, previous) => {
                if (state.session !== previous.session || state.rev !== previous.rev) sync();
            });
        },
    };
}
