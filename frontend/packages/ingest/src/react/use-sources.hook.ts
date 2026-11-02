// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useShallow } from "zustand/react/shallow";
import { type SourceControllerStore, type SourceInfo } from "../source-controller.store";
import type { Source, SourceState } from "../sources/Source";
import type { LocalSource } from "../sources/LocalSource";
import { useIngestContext } from "./IngestContext";
import { useEffect, useMemo } from "react";

export function useSources<T>(selector: (states: Readonly<SourceInfo>) => T): T {
    const { controller } = useIngestContext();
    return controller((state) => selector(state.sourceStates));
}

export function useSourceState(source: string): SourceState | undefined {
    return useSources((states) => states[source]);
}

export function useSourcesWithState(state: SourceState): string[] {
    return useSources(
        useShallow((states) =>
            Object.entries(states)
                .filter(([, st]) => st === state)
                .map(([id]) => id),
        ),
    );
}

/**
 * The locally-drawn-element source. It is an ordinary source: callers include
 * it in the slice they hand to {@link useContributeSources}, so it is admitted
 * and evicted by the same reconcile as every other source.
 */
export function useLocalSource(): LocalSource {
    return useIngestContext().localSource;
}

/**
 * Contribute this feature's named slice of the desired source set, and enable
 * the policy-allowed sources in it. The whole live/historical switch is a
 * contributor changing its slice.
 *
 * `namespace` must be stable and unique to this contributor. `sources` must be
 * referentially stable (memoize it) — a fresh array each render re-runs the
 * reconcile for no reason.
 */
export function useContributeSources(namespace: string, sources: readonly Source[]): void {
    const { controller, policy } = useIngestContext();

    // Publish the slice on every change. `reconcile` already diffs the new slice
    // against the previous one — keeping sources whose id is unchanged and
    // evicting only those that dropped out — so publishing the new set directly
    // is all that's needed.
    useEffect(() => {
        const { reconcile, enable } = controller.getState();
        reconcile(namespace, sources);

        for (const source of sources) {
            if (policy?.shouldEnableOnRegister?.(source.id)) {
                enable(source.id);
            }
        }
    }, [controller, policy, namespace, sources]);

    // Withdraw the whole slice on true unmount only. This MUST be its own effect
    // (deps exclude `sources`): if it shared the effect above, React would run
    // its cleanup before every `sources` change, wiping the slice to empty and
    // re-admitting it — which evicts and purges even the sources that didn't
    // change, defeating the diffing the reconcile above relies on.
    useEffect(() => {
        return () => controller.getState().reconcile(namespace, []);
    }, [controller, namespace]);
}

export interface SourceActions {
    enable(id: string): void;
    disable(id: string): void;
    getStates(): Readonly<SourceInfo>;
}

export function createActions(controller: SourceControllerStore): SourceActions {
    return {
        enable: controller.getState().enable,
        disable: controller.getState().disable,
        getStates: () => controller.getState().sourceStates,
    };
}

export function useSourceActions(): SourceActions {
    const { controller } = useIngestContext();
    return useMemo(() => createActions(controller), [controller]);
}
