// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { noop } from "lodash";
import type { ContributionContext } from "../contribution-context.ts";
import type { ContainmentLog } from "../containment/index.ts";
import type { Problem } from "../validation/index.ts";
import { createJournal } from "./journal.ts";
import { ContributionRefusal } from "./refusal.ts";
import { Registry } from "./registry.ts";

/** A native plugin as the composition root lists it. */
export interface NativeSubject {
    /** The subject's namespaced owner id (`opencop.shapes`). */
    readonly id: string;
    /**
     * Register the subject's contributions through `ctx`. May return a disposer
     * for side effects outside the context (a feature-flag subscription), as a
     * plugin's `uninstall` does.
     */
    install(ctx: ContributionContext): void | (() => void);
}

/**
 * How one native subject's install went. `failed` carries the reason — the registry's
 * own refusal message, or `subject threw during install: …` — and, for a refusal, the
 * rules it broke.
 */
export interface NativeSubjectRecord {
    readonly id: string;
    readonly status: "active" | "failed";
    readonly error?: string;
    readonly problems?: readonly Problem[];
}

export interface NativeInstaller {
    /**
     * Install native subjects, each as its own transaction, and return one disposer
     * for all of them.
     *
     * Each subject registers live through a journaling wrapper around
     * `contextFor(subject.id)`: every registration's disposer is journaled the moment
     * it succeeds. A subject **fails as a whole** when its `install` throws or a
     * registry refuses one of its registrations — during install or later (a flag
     * turning a feature on). Failing rolls its journal back newest first, records it
     * `failed`, logs it once, and makes its context inert, so it leaves nothing
     * behind and registers nothing afterwards. The next subject installs either way.
     *
     * Disposing runs every journal, subjects in reverse, each disposer contained,
     * clears the records, and returns what the disposers threw (also logged). A subject
     * that registers after it was disposed is ignored and warned about: something it
     * started outlived its disposer.
     */
    install(
        subjects: readonly NativeSubject[],
        contextFor: (subjectId: string) => ContributionContext,
    ): () => readonly unknown[];
    /** Every native subject's record, in install order. A fresh array on each change. */
    readonly getRecords: () => readonly NativeSubjectRecord[];
}

/** The native install path. One instance per host. */
export function createNativeInstaller({ log }: { readonly log: ContainmentLog }): NativeInstaller {
    const records = new Registry<NativeSubjectRecord>();

    function setRecord(record: NativeSubjectRecord): void {
        const current = records.getAll();
        records.replace(
            current.some(({ id }) => id === record.id)
                ? current.map((existing) => (existing.id === record.id ? record : existing))
                : [...current, record],
        );
    }

    function logContained(subjectId: string, errors: readonly unknown[]): void {
        for (const error of errors)
            log.error(`[native-install]: undo threw for native subject "${subjectId}":`, error);
    }

    function installOne(
        subject: NativeSubject,
        live: ContributionContext,
    ): () => readonly unknown[] {
        const journal = createJournal();
        let installing = true;
        let inert = false;
        let disposed = false;

        const fail = (reason: string, error: unknown) => {
            if (inert) return;
            inert = true;
            logContained(subject.id, journal.rollback());
            setRecord({
                id: subject.id,
                status: "failed",
                error: reason,
                ...(error instanceof ContributionRefusal ? { problems: error.problems } : null),
            });
            log.error(`[native-install]: native subject "${subject.id}" failed: ${reason}`, error);
        };

        /**
         * Run one registration and journal its disposer. A refusal fails the subject;
         * during install it is rethrown so the installer stops, after install it is not —
         * the caller is then a flag callback, where a throw would surface as uncaught.
         */
        const record = (register: () => () => void): (() => void) => {
            if (disposed)
                log.warn(
                    `[native-install]: native subject "${subject.id}" registered after it was disposed`,
                );
            if (inert) return noop;
            try {
                return journal.add(register());
            } catch (error) {
                fail(String(error), error);
                if (installing) throw error;
                return noop;
            }
        };

        // Every member journals: a context member added without an entry here is a
        // compile error, not an unjournaled registration.
        const ctx: ContributionContext = {
            registerTool: (tool) => record(() => live.registerTool(tool)),
            registerElementMapper: (mapper) => record(() => live.registerElementMapper(mapper)),
            registerPickResponse: (id, response) =>
                record(() => live.registerPickResponse(id, response)),
            registerDetailsHeader: (contribution) =>
                record(() => live.registerDetailsHeader(contribution)),
            registerDetailsPosition: (contribution) =>
                record(() => live.registerDetailsPosition(contribution)),
            registerDetailsEditForm: (contribution) =>
                record(() => live.registerDetailsEditForm(contribution)),
            registerDetailsInfoSection: (contribution) =>
                record(() => live.registerDetailsInfoSection(contribution)),
            registerFilterField: (field) => record(() => live.registerFilterField(field)),
            registerContextMenuItem: (item) => record(() => live.registerContextMenuItem(item)),
        };

        try {
            const dispose = subject.install(ctx);
            if (dispose) {
                if (inert) logContained(subject.id, runContained(dispose));
                else journal.add(dispose);
            }
        } catch (error) {
            fail(`subject threw during install: ${String(error)}`, error);
        }
        installing = false;
        if (!inert) setRecord({ id: subject.id, status: "active" });

        return () => {
            inert = true;
            disposed = true;
            const errors = journal.rollback();
            logContained(subject.id, errors);
            return errors;
        };
    }

    return {
        install(subjects, contextFor) {
            const disposers = subjects.map((subject) =>
                installOne(subject, contextFor(subject.id)),
            );
            return () => {
                const errors = disposers.reverse().flatMap((dispose) => dispose());
                records.replace([]);
                return errors;
            };
        },
        getRecords: records.getAll,
    };
}

function runContained(dispose: () => void): unknown[] {
    try {
        dispose();
        return [];
    } catch (error) {
        return [error];
    }
}
