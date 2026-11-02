// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * Ambient's handler set — what the pointer means when NO session owns it and
 * no map surface is open. A primary click is dispatched by what was under the
 * pointer: an element is selected (or, when several overlap, disambiguated);
 * a hit that declared a registered response's id is that response's
 * (`../pick-responses.ts`);
 * empty ground dismisses. A right-click opens the map context menu.
 *
 * Ambient is the pointer's last owner (`../derive/pointer-ladder.ts`), bound
 * by the runner only while it wins, so there is no per-event gate in this
 * file: a right-click that belongs to a session (a vertex undo, a trace abort)
 * never reaches the context-menu trigger, because while a session is live the
 * trigger is not bound.
 */
import { noop, uniqBy } from "lodash";
import { getElementKey } from "@opencop/elements";
import { isPrimaryButton, type InputSubscription, type InteractionHost } from "../../contract";
import { pickResponseFor } from "../pick-responses";
import type { PointerOwner } from "../pointer-owners";
import { invokeContextMenu } from "../surface";
import { isStorePick, pickTargetOf } from "./pick-target";

export function bindAmbientPointer(host: InteractionHost): InputSubscription[] {
    const { sources, actions } = host;

    const click = sources.onDeck("onClick", (info, element) => {
        // Right-click NEVER clears the selection — ambient acts on the primary
        // button only.
        if (!isPrimaryButton(info)) return;

        // A hit that declared a registered response's id, with no element
        // beneath (elements first — the registry's rule): the click is that
        // response's alone.
        const target = pickTargetOf(info, element);
        const response = pickResponseFor(target);
        if (response && isStorePick(target)) {
            response.onClick(target);
            return;
        }

        const uniqueElements = uniqBy(info.picks, getElementKey);

        if (uniqueElements.length > 1) {
            actions.disambiguate(
                uniqueElements,
                { x: info.picking.x, y: info.picking.y },
                info.picks.length >= 20,
            );
            return;
        }

        actions.dismissDisambiguation();

        // The element under the pointer: the nearest pick when it is one — or,
        // when a feature's drawing sits on top of it (elements first refused
        // its response), the element beneath. Without a hit on top the stack is
        // not consulted, so a click that merely lands near an element still
        // dismisses as it always has.
        const nearest = element ?? (target.hit ? uniqueElements[0] : undefined);
        if (nearest) {
            actions.selectElement(nearest);
        } else {
            actions.dismissSelection();
        }
    });

    // The surface slot's open trigger. The browser's own menu is suppressed
    // only when the map's is there to open in its place.
    const menu = sources.onMap("contextmenu", (ev) => {
        const opened = invokeContextMenu({
            screen: { x: ev.point.x, y: ev.point.y },
            client: { x: ev.originalEvent.clientX, y: ev.originalEvent.clientY },
            lngLat: { lng: ev.lngLat.lng, lat: ev.lngLat.lat },
        });
        if (opened) ev.preventDefault();
    });

    return [click, menu];
}

/** Ambient as the pointer channel sees it: always active, one handler set. */
export const ambientPointerOwner: PointerOwner = {
    isActive: () => true,
    identity: () => "ambient",
    bind: bindAmbientPointer,
    subscribe: () => noop,
};
