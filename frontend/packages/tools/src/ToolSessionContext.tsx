// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { noop } from "lodash";
import { createContext, use, type ReactElement, type ReactNode } from "react";
import type { ToolSession } from "./tool-session";

const ToolSessionContext = createContext<ToolSession | null>(null);
const ToolSessionRevisionContext = createContext(0);

const IDLE_SESSION: ToolSession = {
    draft: null,
    params: {},
    phase: "idle",
    setParams: noop,
    invalidate: noop,
    activate: noop,
    cancel: noop,
};

export interface ToolSessionScopeProps {
    /** The session, already scoped to one tool. */
    readonly session: ToolSession;
    /** The revision of that tool's draft; bumps on any `invalidate`, whatever the attribute. */
    readonly revision: number;
    readonly children: ReactNode;
}

/**
 * Opens a tool's session to the editor below it. The host renders it around a
 * tool's editor; an editor reads it with {@link useToolSession} and
 * {@link useToolSessionRevision}, so a native editor and a plugin editor import
 * the same two hooks from this leaf.
 *
 * The revision has its own context: it changes on every pointer move while
 * drawing, and only a component that reads it re-renders.
 */
export function ToolSessionScope({
    session,
    revision,
    children,
}: ToolSessionScopeProps): ReactElement {
    return (
        <ToolSessionContext value={session}>
            <ToolSessionRevisionContext value={revision}>{children}</ToolSessionRevisionContext>
        </ToolSessionContext>
    );
}

/**
 * The {@link ToolSession} of the tool whose editor this is. Outside a
 * {@link ToolSessionScope} it throws in development and reads as idle in
 * production.
 */
export function useToolSession(): ToolSession {
    const session = use(ToolSessionContext);
    if (session) return session;
    if (import.meta.env.DEV) {
        throw new Error("useToolSession must be used within a ToolSessionScope");
    }
    return IDLE_SESSION;
}

/**
 * The revision of the scoped tool's draft, bumped whenever the draft changes or
 * is invalidated — whatever the attribute, geometry or style. Read it beside a
 * value derived from the draft, so the value is recomputed when the draft is
 * mutated in place. 0 outside a scope.
 */
export function useToolSessionRevision(): number {
    return use(ToolSessionRevisionContext);
}
