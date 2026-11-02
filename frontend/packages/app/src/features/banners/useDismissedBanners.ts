// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useCallback, useState } from "react";

const STORAGE_KEY = "dismissed_banners";

function loadDismissed(): Set<string> {
    try {
        const raw = sessionStorage.getItem(STORAGE_KEY);
        if (!raw) return new Set();
        const parsed = JSON.parse(raw);
        if (Array.isArray(parsed)) return new Set<string>(parsed);
    } catch {
        // ignore malformed storage
    }
    return new Set();
}

export function useDismissedBanners() {
    const [dismissedIds, setDismissedIds] = useState<Set<string>>(loadDismissed);

    const dismiss = useCallback((id: string) => {
        setDismissedIds((prev) => {
            const next = new Set(prev);
            next.add(id);
            try {
                sessionStorage.setItem(STORAGE_KEY, JSON.stringify([...next]));
            } catch {
                // ignore storage errors
            }
            return next;
        });
    }, []);

    return { dismissedIds, dismiss };
}
