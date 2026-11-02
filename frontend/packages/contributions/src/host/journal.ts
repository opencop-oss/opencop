// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * An owner's undo log: every registration's disposer is recorded the moment the
 * registration succeeds, so a failure part-way through an install — or a later
 * unload — can undo exactly what landed.
 *
 * A generic primitive: one journal per plugin in the plugin manager, one per native
 * subject in the native installer.
 */
export interface Journal {
    /**
     * Record `dispose`. Returns a wrapped disposer that runs `dispose` at most
     * once and removes itself from the journal, so an owner that undoes one of
     * its own registrations early (a feature flag turning off) neither grows the
     * journal nor has the disposer run again at rollback.
     */
    add(dispose: () => void): () => void;
    /**
     * Run every recorded disposer, newest first, each contained: one that throws
     * does not stop the others. Empties the journal, so a second call does
     * nothing. Returns the errors it contained, for the caller to log.
     */
    rollback(): readonly unknown[];
}

export function createJournal(): Journal {
    const entries = new Set<() => void>();
    return {
        add(dispose) {
            let done = false;
            const wrapped = () => {
                if (done) return;
                done = true;
                entries.delete(wrapped);
                dispose();
            };
            entries.add(wrapped);
            return wrapped;
        },
        rollback() {
            const errors: unknown[] = [];
            for (const dispose of [...entries].reverse()) {
                try {
                    dispose();
                } catch (error) {
                    errors.push(error);
                }
            }
            return errors;
        },
    };
}
