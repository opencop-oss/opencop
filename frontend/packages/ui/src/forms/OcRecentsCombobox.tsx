// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { createContext, use, useMemo, useState, type ReactNode, type RefObject } from "react";
import { useTranslation } from "react-i18next";
import { createStore, useStore } from "zustand";
import { persist } from "zustand/middleware";
import { OcGroupCombobox, UNGROUPED_GROUP_ID } from "./OcGroupCombobox.tsx";

// ── Recents store ────────────────────────────────────────────────────────────

interface RecentItem {
    /** Stored for potential future fallback use; the displayed label always comes from the live items list. */
    label: string;
    value: string;
}

interface RecentItemsActions {
    addRecentItem: (item: RecentItem) => void;
}

interface RecentItemsState {
    recentItems: RecentItem[];
    actions: RecentItemsActions;
}

// Store a deeper history than we display: a stale (e.g. deleted) recent is only pruned from
// storage lazily, one at a time, as new items get picked — so the display-time liveness filter
// needs headroom to still surface `maxItems` live entries even with a few dead ones ahead of them.
const STORED_MULTIPLE = 3;

function createRecentItemsStore(storageKey: string, maxItems: number) {
    const storedCapacity = maxItems * STORED_MULTIPLE;
    return createStore<RecentItemsState>()(
        persist(
            (set) => ({
                recentItems: [],
                actions: {
                    addRecentItem: (item) =>
                        set((state) => ({
                            recentItems: [
                                item,
                                ...state.recentItems.filter((r) => r.value !== item.value),
                            ].slice(0, storedCapacity),
                        })),
                },
            }),
            {
                name: storageKey,
                partialize: (state) => ({ recentItems: state.recentItems }),
                // Validate and sanitize on hydration: zustand's default merge copies in any valid JSON
                // verbatim, so a non-array recentItems (manual tampering, schema migration) would
                // crash the component on the next render with "recentItems.map is not a function".
                merge: (persisted, current) => {
                    const raw = (persisted as Partial<RecentItemsState> | undefined)?.recentItems;
                    const recentItems = Array.isArray(raw)
                        ? raw
                              .filter(
                                  (r): r is RecentItem =>
                                      !!r &&
                                      typeof r.value === "string" &&
                                      typeof r.label === "string",
                              )
                              .slice(0, storedCapacity)
                        : [];
                    return { ...current, recentItems };
                },
            },
        ),
    );
}

type RecentItemsStoreApi = ReturnType<typeof createRecentItemsStore>;

const RecentItemsContext = createContext<RecentItemsStoreApi | null>(null);

function RecentItemsProvider({
    storageKey,
    maxItems,
    children,
}: {
    storageKey: string;
    maxItems: number;
    children: ReactNode;
}) {
    const [store] = useState(() => createRecentItemsStore(storageKey, maxItems));
    return <RecentItemsContext value={store}>{children}</RecentItemsContext>;
}

function useRecentItemsStore<T>(selector: (state: RecentItemsState) => T): T {
    const store = use(RecentItemsContext);
    if (!store) throw new Error("useRecentItemsStore must be used within RecentItemsProvider");
    return useStore(store, selector);
}

function useRecentItems() {
    return useRecentItemsStore((s) => s.recentItems);
}

function useRecentItemsActions() {
    return useRecentItemsStore((s) => s.actions);
}

// ── OcRecentsComboBox ──────────────────────────────────────────────────────────

const RECENTS_GROUP_ID = "recents";
const DEFAULT_MAX_ITEMS = 5;

export interface OcRecentsComboboxItem {
    label: string;
    value: string;
}

export interface OcRecentsComboboxProps {
    items: OcRecentsComboboxItem[];
    value: string;
    onValueChange: (value: string) => void;
    /**
     * localStorage key for the recents list. Each unique key is an isolated history.
     * Changing this prop remounts the internal store and rehydrates from the new bucket.
     */
    storageKey: string;
    /** Label for the recents group. Defaults to "Recent". */
    recentsLabel?: string;
    /** Visible label for the ungrouped section. When omitted the section is sr-only labelled. */
    ungroupedLabel?: string;
    /**
     * Maximum number of recent items to show. Defaults to 5.
     * Changing this prop remounts the internal store and rehydrates, the same as `storageKey`.
     */
    maxItems?: number;
    placeholder?: string;
    disabled?: boolean;
    showTags?: boolean;
    emptyMessage?: string;
    renderItem?: (item: OcRecentsComboboxItem) => ReactNode;
    portal?: boolean;
    openOnClick?: boolean;
    containerRef?: RefObject<HTMLElement | null>;
}

function OcRecentsComboBoxInner({
    items,
    value,
    onValueChange,
    recentsLabel,
    ungroupedLabel,
    renderItem,
    maxItems,
    ...rest
}: Omit<OcRecentsComboboxProps, "storageKey" | "maxItems"> & { maxItems: number }) {
    const { t } = useTranslation();
    const recentItems = useRecentItems();
    const { addRecentItem } = useRecentItemsActions();

    const groupedItems = useMemo(() => {
        const itemMap = new Map(items.map((i) => [i.value, i]));
        // Cap AFTER the liveness filter: the store holds more than maxItems so that stale (e.g.
        // deleted) recents don't crowd live ones out of the displayed cap.
        const recents = recentItems
            .flatMap((r) => {
                const item = itemMap.get(r.value);
                return item ? [{ label: item.label, value: r.value, group: RECENTS_GROUP_ID }] : [];
            })
            .slice(0, maxItems);
        // Exclude only what the Recent group actually shows — a live recent sitting past the cap
        // (stored but not displayed) must still fall back into the ungrouped list, not vanish.
        const shownIds = new Set(recents.map((r) => r.value));
        const others = items
            .filter((i) => !shownIds.has(i.value))
            .map((i) => ({ label: i.label, value: i.value, group: UNGROUPED_GROUP_ID }));
        return [...recents, ...others];
    }, [items, recentItems, maxItems]);

    const groups = useMemo(
        () => [{ id: RECENTS_GROUP_ID, label: recentsLabel ?? t("common.recents") }],
        [recentsLabel, t],
    );

    // Recents follow selection intent, not a committed save — cancelling the form still leaves the pick.
    const handleValueChange = (v: string | string[]) => {
        // OcGroupCombobox always delivers a string here (multiple is not forwarded), but the wider
        // union satisfies the OcGroupCombobox callback contract.
        const selected = typeof v === "string" ? v : (v[0] ?? "");
        onValueChange(selected);
        if (selected) {
            const item = items.find((i) => i.value === selected);
            // Recents tracking is best-effort; a QuotaExceededError must never block the primary selection.
            if (item)
                try {
                    addRecentItem({ label: item.label, value: item.value });
                } catch {
                    /* ignore */
                }
        }
    };

    return (
        <OcGroupCombobox
            items={groupedItems}
            groups={groups}
            value={value}
            onValueChange={handleValueChange}
            ungroupedLabel={ungroupedLabel}
            renderItem={renderItem}
            {...rest}
        />
    );
}

/**
 * Combobox with a "Recent" group at the top showing the last `maxItems` (default 5) selected items.
 * Each `storageKey` maintains an independent persisted history.
 * Instances do not share live state: mount at most one per `storageKey` at a time, or picks in one
 * overwrite the other's.
 *
 * @example
 * // Watchbox data-source picker with its own recents history, separate from the event layer picker.
 * <OcRecentsComboBox
 *   items={dataSources.map((s) => ({ label: s.name, value: s.id }))}
 *   value={selectedSourceId}
 *   onValueChange={setSelectedSourceId}
 *   storageKey="watchbox:recent-sources"
 *   recentsLabel="Recent Sources"
 *   ungroupedLabel="All Sources"
 *   placeholder="Search sources..."
 * />
 */
export function OcRecentsComboBox({
    storageKey,
    maxItems = DEFAULT_MAX_ITEMS,
    ...props
}: OcRecentsComboboxProps) {
    // Negative/fractional values are nonsensical here — slice(0, -1) means "all but the last
    // one", so an unclamped negative maxItems would show nearly the whole history.
    const cap = Math.max(0, Math.trunc(maxItems));
    return (
        // key= includes the cap so either prop remounts the provider and rehydrates from scratch,
        // rather than leaving maxItems captured by the store's initial useState.
        <RecentItemsProvider key={`${storageKey}:${cap}`} storageKey={storageKey} maxItems={cap}>
            <OcRecentsComboBoxInner maxItems={cap} {...props} />
        </RecentItemsProvider>
    );
}
