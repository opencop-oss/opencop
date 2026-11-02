// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useCallback, useMemo } from "react";
import type { ToolParams, ToolPhase, ToolSession } from "@opencop/tools";
import {
    activate,
    cancel,
    draftChanged,
    exit,
    setParams as setSessionParams,
} from "~/features/map/interaction-runtime/session/commands";
import {
    getInteractionState,
    sessionDraft,
    useInteraction,
    type Session,
} from "~/features/map/interaction-runtime/session/interaction.store";

/**
 * The host's {@link ToolSession} for one tool — what the editor outlet puts in a
 * `ToolSessionScope` around that tool's editor.
 *
 * Every read and every command checks that `toolId` owns the session: while
 * another tool's session runs, or none does, the view reads as idle and its
 * commands do nothing — except `activate`, which may also arm the tool when no
 * session runs. Commands check when they run, so one kept in a closure cannot
 * reach a session that started after it was read.
 */
export function useScopedToolSession(toolId: string): ToolSession {
    const draft = useInteraction((s) => (owns(s.session, toolId) ? sessionDraft(s.session) : null));
    // Both draw and edit sessions carry the params the product was drawn with.
    const params = useInteraction((s) =>
        owns(s.session, toolId) ? s.session.params : EMPTY_PARAMS,
    );
    // The session kinds ARE the editor's phases: a finished product held for
    // adjustment is an edit session — custody or not — and reports `edit`
    // like any shape's, because it is one.
    const phase = useInteraction(
        (s): ToolPhase => (owns(s.session, toolId) ? s.session.kind : "idle"),
    );

    // Commands are stable module functions — a setter is not render state.
    const setParams = useCallback(
        (next: ToolParams) => {
            if (ownsNow(toolId)) setSessionParams(next);
        },
        [toolId],
    );

    // A non-element draft redraws on the session's revision; an element draft
    // redraws itself through the element store's draft listeners.
    const invalidate = useCallback(() => {
        if (ownsNow(toolId)) draftChanged();
    }, [toolId]);

    const activateTool = useCallback(
        (next?: ToolParams) => {
            // Idle is allowed: an editor re-arms after its own `cancel()` (a graphic change).
            const { session } = getInteractionState();
            if (session.kind !== "idle" && !owns(session, toolId)) return;
            activate(toolId, next ? { params: next } : {});
        },
        [toolId],
    );

    const abandon = useCallback(() => {
        const { session } = getInteractionState();
        if (!owns(session, toolId)) return;
        // Editors say "cancel" for both halves of the lifecycle: cancel the
        // edit when one is open, otherwise leave the session entirely.
        if (session.kind === "edit") cancel();
        else exit();
    }, [toolId]);

    return useMemo(
        () => ({
            draft,
            params,
            phase,
            setParams,
            invalidate,
            activate: activateTool,
            cancel: abandon,
        }),
        [draft, params, phase, setParams, invalidate, activateTool, abandon],
    );
}

/**
 * The revision of `toolId`'s draft, bumped on any `invalidate` whatever the attribute;
 * 0 while another tool, or none, owns the session.
 */
export function useScopedToolSessionRevision(toolId: string): number {
    return useInteraction((s) => (owns(s.session, toolId) ? s.rev : 0));
}

function owns(session: Session, toolId: string): session is Exclude<Session, { kind: "idle" }> {
    return session.kind !== "idle" && session.descriptorId === toolId;
}

function ownsNow(toolId: string): boolean {
    return owns(getInteractionState().session, toolId);
}

const EMPTY_PARAMS: ToolParams = {};
