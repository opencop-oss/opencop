// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * The EditModel interpreter — one drag loop for every draft type. Binds
 * mousemove/mousedown/mouseup through the host's sources and interprets the
 * model's control points, hit regions, and drag math.
 *
 * Hit-testing projects through the host's live viewport (always current),
 * never a hover-captured one — plain synthetic mouse events drive it.
 *
 * The host owns the grab tolerance and passes it into every `hitTest`: how many
 * pixels count as "on the handle" is a property of the running app (pointer
 * type, display density), not of any one draft's geometry, so a model must
 * never bake its own.
 */
import { assertNever } from "assert-never";
import { MercatorCoordinate, type MapMouseEvent } from "maplibre-gl";
import type { DraftObject } from "@opencop/elements";
import type { ControlPoint, EditModel } from "@opencop/tools";
import type { InputSubscription, InteractionSources } from "../contract";
import { setEditSub } from "./commands";
import { emptySub } from "./interaction.store";
import { captureOriginalPositions } from "./custody";
import { hitControlPoint } from "@opencop/tools";

/** What the interpreter needs from its binder. */
export interface EditSessionHost {
    /** The pointer bindings — map events, plus the document for a release off the canvas. */
    readonly sources: Pick<InteractionSources, "onMap" | "onDocument">;
    /** A lng/lat pair to canvas pixels through the live viewport. */
    project(lngLat: [number, number]): [number, number] | undefined;
    getDraft(): DraftObject | null;
}

type DragTarget =
    | { kind: "handle"; controlPoint: ControlPoint; snapshot: readonly number[] }
    | { kind: "boundary" }
    | { kind: "translate"; last: [number, number] };

/** Host grab tolerance, in screen pixels — passed to every model's `hitTest`. */
const DRAG_TOLERANCE_PX = 15;

/** Default whole-draft translate: incremental Mercator-offset drag math. */
function defaultTranslate(draft: DraftObject, from: [number, number], to: [number, number]): void {
    const fromMerc = MercatorCoordinate.fromLngLat({ lng: from[0], lat: from[1] });
    const toMerc = MercatorCoordinate.fromLngLat({ lng: to[0], lat: to[1] });
    draft.geometry.translate(toMerc.x - fromMerc.x, toMerc.y - fromMerc.y);
}

/**
 * Bind the interpreter for one EDIT session, with or without custody.
 * Returns the subscriptions for the binder to dispose.
 */
export function bindEditSession(model: EditModel, host: EditSessionHost): InputSubscription[] {
    const { sources } = host;
    let drag: DragTarget | null = null;

    const project = (lngLat: [number, number]) => host.project(lngLat);

    function resolveIntent(ev: MapMouseEvent) {
        const draft = host.getDraft();
        if (!draft) return { draft: null, intent: { kind: "none" as const } };
        const lngLat = ev.lngLat.toArray() as [number, number];
        const screen = project(lngLat);
        if (!screen) return { draft, intent: { kind: "none" as const } };
        if (model.hitTest) {
            const intent = model.hitTest(draft, screen, lngLat, project, DRAG_TOLERANCE_PX);
            // A model that never moves its product as a whole ignores its body.
            return {
                draft,
                intent:
                    intent.kind === "body" && model.translate === false
                        ? { kind: "none" as const }
                        : intent,
            };
        }
        // Default: the nearest control point within the host tolerance, else nothing.
        const cp = hitControlPoint(model.controlPoints(draft), screen, project, DRAG_TOLERANCE_PX);
        return {
            draft,
            intent: cp ? { kind: "handle" as const, controlPoint: cp } : { kind: "none" as const },
        };
    }

    const onMouseMove = (ev: MapMouseEvent) => {
        const draft = host.getDraft();
        if (!draft) return;
        const lngLat = ev.lngLat.toArray() as [number, number];

        if (drag) {
            switch (drag.kind) {
                case "handle":
                    model.applyHandle(draft, drag.controlPoint, lngLat, drag.snapshot);
                    break;
                case "boundary":
                    model.applyBoundary?.(draft, lngLat);
                    break;
                case "translate": {
                    const translate = model.translate || defaultTranslate;
                    translate(draft, drag.last, lngLat);
                    drag.last = lngLat;
                    break;
                }
                default:
                    assertNever(drag);
            }
            return;
        }

        // Not dragging — resolve the hotspot for the cursor derivation.
        const { intent } = resolveIntent(ev);
        setEditSub({
            hotspot:
                intent.kind === "handle"
                    ? "handle"
                    : intent.kind === "boundary"
                      ? "boundary"
                      : intent.kind === "body"
                        ? "body"
                        : null,
            dragging: null,
        });
    };

    const onMouseDown = (ev: MapMouseEvent) => {
        if (ev.originalEvent.button !== 0) return;
        const { draft, intent } = resolveIntent(ev);
        if (!draft) return;

        switch (intent.kind) {
            case "handle":
                drag = {
                    kind: "handle",
                    controlPoint: intent.controlPoint,
                    snapshot: captureOriginalPositions(draft),
                };
                setEditSub({ hotspot: "handle", dragging: "handle" });
                break;
            case "boundary":
                drag = { kind: "boundary" };
                setEditSub({ hotspot: "boundary", dragging: "boundary" });
                break;
            case "body":
                drag = { kind: "translate", last: ev.lngLat.toArray() as [number, number] };
                setEditSub({ hotspot: "body", dragging: "translate" });
                break;
            case "none":
                break;
            default:
                assertNever(intent);
        }
    };

    const release = () => {
        if (!drag) return;
        drag = null;
        setEditSub(emptySub());
    };

    return [
        sources.onMap("mousemove", onMouseMove),
        sources.onMap("mousedown", onMouseDown),
        sources.onMap("mouseup", release),
        // A mouseup outside the canvas must still end the drag.
        sources.onDocument("mouseup", release),
    ];
}
