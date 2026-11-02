// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { Element, Geometry } from "@opencop/elements";
import { useCallback, useRef, useSyncExternalStore } from "react";

/**
 * Subscribes to element geometry changes and triggers re-renders.
 * Uses useSyncExternalStore with a revision counter to avoid infinite loops
 * (returning objects from getSnapshot causes reference comparison failures).
 *
 * Call sites derive their own return values after the subscription triggers a re-render.
 *
 * NOTE: See Reactive.listen() for listener semantics — this hook adds
 * one of potentially many listeners on the geometry's Reactive system.
 */
export function useGeometrySubscription(element: Element<Geometry>): void {
    const revisionRef = useRef(0);

    const subscribe = useCallback(
        (onStoreChange: () => void) => {
            const unsubscribe = element.geometry.listen(() => {
                revisionRef.current += 1;
                onStoreChange();
            });
            return unsubscribe;
        },
        [element],
    );

    const getSnapshot = useCallback(() => revisionRef.current, []);

    useSyncExternalStore(subscribe, getSnapshot);
}
