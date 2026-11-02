// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { createContext, use, useEffect, useMemo, type ReactNode } from "react";

interface DrawerContextValue {
    onClose: () => void;
    /**
     * Reports that the drawer owns an in-flight request. Dismissing while one is pending unmounts
     * the only consumer of its result, so a drawer that receives this refuses to close until the
     * request settles. Defaults to a no-op for drawers that do not opt in.
     */
    setBusy: (busy: boolean) => void;
}

const noop = () => {};

const DrawerContext = createContext<DrawerContextValue | null>(null);

export function useDrawerContext(): DrawerContextValue {
    const ctx = use(DrawerContext);
    if (!ctx) throw new Error("useDrawerContext must be used within DrawerContextProvider");
    return ctx;
}

/**
 * Holds the enclosing drawer open while `busy` is true, so an in-flight mutation keeps the owner of
 * its success/error callbacks mounted. Safe to call outside a drawer, where there is nothing to
 * guard — components using this are also rendered standalone in tests.
 */
export function useDrawerBusy(busy: boolean): void {
    const setBusy = use(DrawerContext)?.setBusy;
    useEffect(() => {
        if (!setBusy) return;
        setBusy(busy);
        return () => setBusy(false);
    }, [busy, setBusy]);
}

export function DrawerContextProvider({
    onClose,
    setBusy,
    children,
}: {
    onClose: () => void;
    setBusy?: (busy: boolean) => void;
    children: ReactNode;
}) {
    const value = useMemo(() => ({ onClose, setBusy: setBusy ?? noop }), [onClose, setBusy]);
    return <DrawerContext value={value}>{children}</DrawerContext>;
}
