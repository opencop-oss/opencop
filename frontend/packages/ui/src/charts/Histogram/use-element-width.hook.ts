// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useEffect, useState, type RefObject } from "react";

/**
 * Track an element's content-box width via `ResizeObserver`. Returns 0 until the
 * first measurement. The observer is attached to the element behind `ref` and
 * torn down on unmount / ref change.
 */
export function useElementWidth<T extends HTMLElement>(ref: RefObject<T | null>): number {
    const [width, setWidth] = useState(0);

    useEffect(() => {
        const el = ref.current;
        if (!el) return;
        const observer = new ResizeObserver((entries) => {
            const entry = entries[0];
            if (entry) setWidth(entry.contentRect.width);
        });
        observer.observe(el);
        return () => observer.disconnect();
    }, [ref]);

    return width;
}
