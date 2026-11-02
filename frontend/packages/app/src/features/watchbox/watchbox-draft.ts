// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ToolDescriptor } from "@opencop/tools";
import { getTool } from "~/features/contributions/tool-registry";
import {
    getInteractionState,
    subscribeInteraction,
} from "~/features/map/interaction-runtime/session/interaction.store";
import { sessionDraft, type Session } from "~/features/map/interaction-runtime/session/session";
import { WATCHBOX_STORE_ID, watchboxDraftOf, type WatchboxStore } from "./watchbox.store";

/** The draft of a session whose tool produces a watchbox, once placed; otherwise null. */
function watchboxDraftOfSession(
    session: Session,
    resolveTool: (id: string) => ToolDescriptor | undefined,
) {
    if (session.kind === "idle" || session.descriptorId === null) return null;
    if (session.kind === "draw" && !session.draft?.placed) return null;
    if (resolveTool(session.descriptorId)?.produces?.store !== WATCHBOX_STORE_ID) return null;
    return sessionDraft(session);
}

/**
 * Hands the watchbox being drawn to `store` while the running session's tool
 * produces a watchbox: set on every change to the draft, cleared when the
 * session ends or moves on. Returns the stop, which clears it too.
 */
export function followWatchboxDraft(
    store: WatchboxStore,
    resolveTool: (id: string) => ToolDescriptor | undefined = getTool,
): () => void {
    const sync = () => {
        const draft = watchboxDraftOfSession(getInteractionState().session, resolveTool);
        if (draft) store.setDraft(watchboxDraftOf(draft));
        else store.clearDraft();
    };
    sync();
    // The draft is mutated in place, so every revision re-sets it.
    const unsubscribe = subscribeInteraction((state, previous) => {
        if (state.session !== previous.session || state.rev !== previous.rev) sync();
    });
    return () => {
        unsubscribe();
        store.clearDraft();
    };
}
