// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { getElementKey, type Element } from "@opencop/elements";
import { useElement } from "@opencop/ingest";
import { getInteractionState } from "~/features/map/interaction-runtime/session/interaction.store";
import { useSelectionActions } from "./core/slices/selection/selection.hook";
import { useViewActions } from "./core/slices/view/view.hook";
import { focusOnElement } from "./element-focus.util";

/**
 * The element identified by `identity`, if currently held locally, plus a
 * click handler that selects it and frames the camera on it. No-ops while an
 * interaction session (draw/edit) is active, so following the link can't
 * steal the camera or selection away from it.
 */
export function useSelectAndFocusElement(identity: { sourceId: string; objectId: string }): {
    element: Element | undefined;
    selectAndFocus: () => void;
} {
    const key = getElementKey(identity);
    const element = useElement(key);
    const { select } = useSelectionActions();
    const { fitBounds, flyTo } = useViewActions();

    return {
        element,
        selectAndFocus: () => {
            // Defensive: callers are expected to render the link only when
            // `element` is set; kept so the contract doesn't depend on that.
            if (!element) return;
            if (getInteractionState().session.kind !== "idle") return;
            select(key);
            focusOnElement(element, { flyTo, fitBounds });
        },
    };
}
