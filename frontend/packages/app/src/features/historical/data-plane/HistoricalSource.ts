// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * One selected subject (data source or layer) of a historical query, exposed as
 * an ordinary {@link Source} so the `SourceController` manages it exactly like a
 * `WebSocketSource`.
 *
 * A historical query is **one transport session** — a single REST submit
 * carrying N subjects, a single per-user WebSocket queue, one heartbeat, one
 * admission slot against the backend's concurrency limit — fanned out to N
 * subjects. {@link HistoricalSession} owns that session; this class is the
 * registration unit layered over it, so the two roles stop being conflated in
 * one object.
 *
 * That split matters for the controller:
 *
 * - **Identity.** The session is identified by its subjects + window, but its
 *   *elements* are stored under the bare subject id. Registering under the
 *   subject id directly would collide with the live `WebSocketSource` for the
 *   same layer, and `reconcile` keeps the existing instance on an id match — so
 *   the historical source would silently never register. Hence the namespaced
 *   `history:<sessionId>:<subjectId>`, with {@link ownedSourceIds} pointing back
 *   at the real subject so purging still finds the rows.
 * - **Lifecycle.** Admitting the first subject starts the session; evicting the
 *   last one stops it. Subject selection changes are therefore ordinary
 *   reconciles rather than bespoke add/remove plumbing.
 */
import type { Element } from "@opencop/elements";
import { Source, type SourceState } from "@opencop/ingest";

import type { HistoricalSession } from "./HistoricalSession";

export class HistoricalSource extends Source {
    private readonly session: HistoricalSession;
    private readonly subjectId: string;
    private detachSink: (() => void) | null = null;

    constructor(session: HistoricalSession, subjectId: string) {
        super(`history:${session.id}:${subjectId}`);
        this.session = session;
        this.subjectId = subjectId;
    }

    /**
     * The subject id, not this source's own id: historical elements are stored
     * under the subject they came from, so this is what the controller must
     * purge. See `Source.ownedSourceIds`.
     */
    override get ownedSourceIds(): readonly string[] {
        return [this.subjectId];
    }

    protected onEnable(): void {
        this.detachSink = this.session.attachSubject(this.subjectId, {
            deliver: (elements: Element[]) => this.handler?.(elements),
            // The session's transport state is this subject's state: one submit,
            // one queue, one heartbeat, so completion and failure are shared.
            setState: (state: SourceState) => this.setState(state),
        });
        this.session.retain();
    }

    protected onDisable(): void {
        this.detachSink?.();
        this.detachSink = null;
        this.session.release();
    }
}
