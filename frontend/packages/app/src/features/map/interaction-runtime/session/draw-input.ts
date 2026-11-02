// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * The runtime's input as a draw grammar sees it. A grammar is host-neutral —
 * it hears `DrawPointerEvent`s, never MapLibre's own events — so this adapter
 * is where a map pointer event becomes one, and where a grammar's release,
 * Enter and click-consuming needs reach the host.
 */
import type { Position2D } from "@opencop/elements";
import type { DrawInput, DrawSubscription } from "@opencop/tools";
import type { InteractionHost } from "../contract";
import { onSessionEnter } from "./session-enter";

export function drawInputOf(host: Pick<InteractionHost, "sources" | "actions">): DrawInput {
    const { sources, actions } = host;
    return {
        onPointer: (type, listener) =>
            sources.onMap(type, (ev) =>
                listener({
                    lngLat: ev.lngLat.toArray() as Position2D,
                    point: ev.point,
                    button: ev.originalEvent.button,
                    preventDefault: () => ev.preventDefault(),
                }),
            ),
        onRelease: (listener) =>
            sources.onDocument("mouseup", (ev) => listener({ button: ev.button })),
        onEnter: (listener) => onSessionEnter(listener),
        consumeNextClick: () => actions.consumeNextDeckClick(),
    };
}

/**
 * `input` with every listener a grammar binds contained: a listener that throws
 * is reported through `onDefect` instead of escaping into the map's event
 * dispatch, and every binding made is recorded in `bound`, so a `bind` that
 * throws part-way can have what it already bound released.
 */
export function containedDrawInput(
    input: DrawInput,
    onDefect: (error: unknown) => void,
): { readonly input: DrawInput; readonly bound: DrawSubscription[] } {
    const bound: DrawSubscription[] = [];
    const guard =
        <A extends unknown[]>(listener: (...args: A) => void) =>
        (...args: A) => {
            try {
                listener(...args);
            } catch (error) {
                onDefect(error);
            }
        };
    const record = (subscription: DrawSubscription) => {
        bound.push(subscription);
        return subscription;
    };
    return {
        bound,
        input: {
            onPointer: (type, listener) => record(input.onPointer(type, guard(listener))),
            onRelease: (listener) => record(input.onRelease(guard(listener))),
            onEnter: (listener) => record(input.onEnter(guard(listener))),
            consumeNextClick: () => input.consumeNextClick(),
        },
    };
}
