// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useEffect, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { useThrottledCallback } from "@tanstack/react-pacer";
import { useElementActions } from "@opencop/ingest";
import { useMapManager } from "~/features/map/MapManagerContext";
import { useMapStore } from "~/features/map/core/map.store";
import { registerKeyBinding } from "~/features/map/interaction-runtime/arbitration/key-bindings";
import { startEdit } from "~/features/map/interaction-runtime/session/commands";
import { useToast } from "~/features/notifications/use-toast.hook";
import { useEventClipboardStore } from "./event-clipboard.store";
import { buildEventClipboardPayload, createPastedEvent } from "./event-clipboard.util";

function hasSelectedText(): boolean {
    return (window.getSelection()?.toString().trim().length ?? 0) > 0;
}

/**
 * The event clipboard: Ctrl/Cmd+C copies the selected element, Ctrl/Cmd+V
 * pastes a new local copy at the pointer and opens it for editing.
 *
 * The two chords are key bindings registered with the interaction runtime
 * (`arbitration/key-bindings.ts`), so the router's one `document` listener
 * carries them: a live session or a focused text control takes a chord first,
 * and the browser keeps it when the clipboard has nothing to act on — no
 * selection, text selected on the page, nothing copied. The bindings never
 * read the session; who receives the chord is the arbiter's decision.
 *
 * The paste drop point is where the pointer last was over the map — the
 * manager's observational read, not a subscription: nothing here competes for
 * the pointer.
 *
 * Pure side-effect component — renders nothing.
 */
export function EventClipboard(): ReactNode {
    const mapManager = useMapManager();
    const { t } = useTranslation();
    const { showToast } = useToast();
    const { get } = useElementActions();

    // Rapid repeats of the chord copy every time but toast at most once a second.
    const showCopyToast = useThrottledCallback(
        () =>
            showToast({
                type: "success",
                title: t("events.copyEvent" as never, "Event copied"),
                description: t(
                    "events.copyEventDesc" as never,
                    "Use Ctrl+V or Cmd+V to paste a new local copy",
                ),
            }),
        { wait: 1000, leading: true, trailing: false },
    );

    useEffect(() => {
        const selectedElement = () => {
            const selected = useMapStore.getState().selected;
            return selected ? get(selected) : undefined;
        };

        const unregisterCopy = registerKeyBinding({
            key: "c",
            modifier: "ctrlOrMeta",
            // Text selected on the page is the browser's to copy.
            canRun: () => !hasSelectedText() && selectedElement() !== undefined,
            run: () => {
                const element = selectedElement();
                if (!element) return;
                useEventClipboardStore
                    .getState()
                    .setCopiedEvent(buildEventClipboardPayload(element));
                showCopyToast();
            },
        });

        const unregisterPaste = registerKeyBinding({
            key: "v",
            modifier: "ctrlOrMeta",
            canRun: () => useEventClipboardStore.getState().copiedEvent !== null,
            run: () => {
                const copied = useEventClipboardStore.getState().copiedEvent;
                if (!copied) return;
                const pasted = createPastedEvent(copied, mapManager.pointerLngLat() ?? undefined);
                if (!pasted) return;
                startEdit(pasted, true);
            },
        });

        return () => {
            unregisterCopy();
            unregisterPaste();
        };
    }, [mapManager, showCopyToast, get]);

    return null;
}
