// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import {
    createContext,
    use,
    useCallback,
    useMemo,
    useState,
    type ReactElement,
    type ReactNode,
} from "react";
import { useSearchParams } from "react-router";

interface ViewDrawerContextValue<M extends { type: string }> {
    selectedId: string | null;
    selectedType: string | null;
    openDrawer: (id: string, type?: string) => void;
    /**
     * Clears `?id`/`?type` and resets `modal` to its mount-time seed, so a dialog opened from
     * drawer content cannot outlive the drawer it was opened from.
     */
    closeDrawer: () => void;
    modal: M;
    /**
     * Value form only. The updater form is deliberately not offered: a view mounts the provider
     * with its full modal union while a drawer consumes a narrower one, so `prev` could be a
     * variant outside the `M` the caller declared.
     */
    setModal: (modal: M) => void;
}

// Untyped (`any`) because the modal type `M` is only known at each provider boundary;
// callers re-parametrize via `useViewDrawerContext<M>()`.
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const ViewDrawerContext = createContext<ViewDrawerContextValue<any> | null>(null);

export function useViewDrawerContext<M extends { type: string }>(): ViewDrawerContextValue<M> {
    const ctx = use(ViewDrawerContext);
    if (!ctx) throw new Error("useViewDrawerContext must be used within ViewDrawerProvider");
    // Sound only when the caller is nested under a provider parametrized with the same `M`.
    return ctx as ViewDrawerContextValue<M>;
}

export function ViewDrawerProvider<M extends { type: string }>({
    initialModal,
    children,
}: {
    initialModal: M;
    children: ReactNode;
}): ReactElement {
    const [searchParams, setSearchParams] = useSearchParams();
    const [modal, setModal] = useState<M>(initialModal);
    // Frozen at mount: call sites pass `initialModal` inline, so depending on the prop would
    // give `closeDrawer` a fresh identity each render and defeat the memo below.
    const [seedModal] = useState(initialModal);

    const openDrawer = useCallback(
        (id: string, type?: string) => {
            setSearchParams(
                (prev) => {
                    prev.set("id", id);
                    if (type !== undefined) {
                        prev.set("type", type);
                    } else {
                        prev.delete("type");
                    }
                    return prev;
                },
                { replace: true },
            );
        },
        [setSearchParams],
    );

    const closeDrawer = useCallback(() => {
        // Also clear the modal: dialogs render on `modal.type` alone, so one left open is
        // orphaned once the drawer closes.
        setModal(seedModal);
        setSearchParams(
            (prev) => {
                prev.delete("id");
                prev.delete("type");
                return prev;
            },
            { replace: true },
        );
    }, [setSearchParams, seedModal]);

    // Derived id/type rather than the `searchParams` object. This does not reduce re-renders:
    // `openDrawer`/`closeDrawer` still change identity on any `location.search` change, since
    // react-router memoizes `setSearchParams` on `searchParams`. It only drops the object dep.
    const selectedId = searchParams.get("id");
    const selectedType = searchParams.get("type");
    const value: ViewDrawerContextValue<M> = useMemo(
        () => ({
            selectedId,
            selectedType,
            openDrawer,
            closeDrawer,
            modal,
            setModal,
        }),
        [selectedId, selectedType, openDrawer, closeDrawer, modal],
    );

    return <ViewDrawerContext value={value}>{children}</ViewDrawerContext>;
}
