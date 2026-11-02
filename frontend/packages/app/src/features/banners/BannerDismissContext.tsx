// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { createContext, use, useCallback, useMemo, useState, type ReactNode } from "react";

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

interface BannerDismissContextType {
    dismissedIds: Set<string>;
    dismiss: (id: string) => void;
}

const BannerDismissContext = createContext<BannerDismissContextType | null>(null);

export function useBannerDismiss(): BannerDismissContextType {
    const ctx = use(BannerDismissContext);
    if (!ctx) throw new Error("useBannerDismiss must be used within BannerDismissProvider");
    return ctx;
}

export function BannerDismissProvider({ children }: { children: ReactNode }) {
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

    const value = useMemo(() => ({ dismissedIds, dismiss }), [dismissedIds, dismiss]);

    return <BannerDismissContext value={value}>{children}</BannerDismissContext>;
}
