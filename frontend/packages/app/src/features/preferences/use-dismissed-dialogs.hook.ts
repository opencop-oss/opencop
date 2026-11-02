// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { getCachedUser } from "@opencop/api";
import { useCallback, useState } from "react";

const STORAGE_KEY_PREFIX = "opencop:dismissedDialogs";

function getUserId(): string | null {
    if (typeof window === "undefined") return null;
    return getCachedUser()?.user?.id ?? null;
}

function storageKey(userId: string): string {
    return `${STORAGE_KEY_PREFIX}:${userId}`;
}

function loadDismissed(userId: string | null): Set<string> {
    if (!userId) return new Set();
    try {
        const raw = localStorage.getItem(storageKey(userId));
        if (!raw) return new Set();
        const parsed: unknown = JSON.parse(raw);
        if (Array.isArray(parsed)) return new Set<string>(parsed);
    } catch {
        // ignore malformed storage
    }
    return new Set();
}

function saveDismissed(userId: string | null, ids: Set<string>): void {
    if (!userId) return;
    try {
        localStorage.setItem(storageKey(userId), JSON.stringify([...ids]));
    } catch {
        // ignore storage errors
    }
}

/**
 * Manages permanently dismissed confirmation dialogs, persisted per-user in localStorage.
 *
 * Dialog IDs follow the convention `"feature/description"`,
 * e.g. `"events/hiddenLayerBroadcast"`.
 */
export function useDismissedDialogs() {
    const [dismissedIds, setDismissedIds] = useState<Set<string>>(() => loadDismissed(getUserId()));

    const isDismissed = useCallback((id: string) => dismissedIds.has(id), [dismissedIds]);

    const dismiss = useCallback((id: string) => {
        setDismissedIds((prev) => {
            const next = new Set(prev);
            next.add(id);
            saveDismissed(getUserId(), next);
            return next;
        });
    }, []);

    const reset = useCallback((id: string) => {
        setDismissedIds((prev) => {
            const next = new Set(prev);
            next.delete(id);
            saveDismissed(getUserId(), next);
            return next;
        });
    }, []);

    return { isDismissed, dismiss, reset };
}
