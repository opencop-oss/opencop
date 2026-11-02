// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { subscribeSessionLifecycle } from "~/features/map/interaction-runtime/session/session-lifecycle";
import { useEventsEditingStore } from "./events-editing.store";

/**
 * The editing overlay follows the custody edit session: it opens (empty) the
 * moment a persisted element enters edit — synchronously, so `EventEditForm`
 * reads overlay state on its first render — and clears when that session
 * ends, however it ends. A custody-less edit (a finished measurement, a
 * watchbox polygon) is not an event edit and never opens it.
 */
export function attachEventsEditingPolicy(): () => void {
    return subscribeSessionLifecycle((event) => {
        const { actions } = useEventsEditingStore.getState();
        switch (event.type) {
            case "edit-started":
                if (event.session.custody) actions.enterEditMode();
                return;
            case "ended":
                if (event.session.kind === "edit" && event.session.custody) actions.clearOverlay();
                return;
            case "activated":
                return;
        }
    });
}
