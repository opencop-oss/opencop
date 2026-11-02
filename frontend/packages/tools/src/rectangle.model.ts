// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * Rectangle edit model — corner drag preserving rectangularity, a rotate handle,
 * plus body translate. Anchors are read from the drag-start snapshot, never the live positions.
 */
import * as turf from "@turf/turf";
import type { DraftObject, PolygonGeometry, RectangleElement } from "@opencop/elements";
import { hitBody, hitControlPoint, vertexControlPoints } from "./edit-helpers";
import type { ControlPoint, EditIntent, EditModel } from "./edit-model";
import { MercatorVector } from "./MercatorVector";
import { positionPairs } from "./screen-math";

/**
 * How far the rotate handle floats off its edge, as a fraction of the rectangle's height.
 * Geographic, while the handle is drawn and hit-tested in pixels, so on a very short box it
 * lands on the outline.
 */
const ROTATE_HANDLE_OFFSET = 0.15;

/**
 * The rotate handle, floating off the edge across from the first. Follows ring slots 0/1,
 * which the host corner drag does not preserve, so on a plain rectangle it can change
 * sides after one.
 */
function rotateControlPoint(draft: DraftObject): ControlPoint | undefined {
    const corners = positionPairs(draft.geometry.getAllPos());
    if (corners.length < 4) return undefined;

    const [a, b, c, d] = corners.map((corner) => MercatorVector.fromLngLat(corner));
    const near = a.clone().add(b).scale(0.5);
    const far = c.clone().add(d).scale(0.5);
    const outward = far.clone().subtract(near);
    if (outward.len() === 0) return undefined;

    const position = far.add(
        outward
            .clone()
            .normalize()
            .scale(outward.len() * ROTATE_HANDLE_OFFSET),
    );
    return { kind: "rotate", position: position.toLngLat().slice(0, 2) as [number, number] };
}

function rectangleControlPoints(draft: DraftObject): ControlPoint[] {
    const rotate = rotateControlPoint(draft);
    return rotate ? [...vertexControlPoints(draft), rotate] : vertexControlPoints(draft);
}

/** Turns the snapshot ring about its centre by the angle the pointer has swept from the grab point. */
function rotateRing(
    reference: readonly number[],
    grab: [number, number],
    lngLat: [number, number],
): number[] | undefined {
    const corners = positionPairs(reference).slice(0, 4);
    if (corners.length < 4) return undefined;

    const vectors = corners.map((corner) => MercatorVector.fromLngLat(corner));
    const centre = vectors[0].clone().add(vectors[2]).scale(0.5);
    const from = MercatorVector.fromLngLat(grab).subtract(centre);
    const to = MercatorVector.fromLngLat(lngLat).subtract(centre);
    if (from.len() === 0 || to.len() === 0) return undefined;

    const sweep = Math.atan2(to.y, to.x) - Math.atan2(from.y, from.x);
    const [cos, sin] = [Math.cos(sweep), Math.sin(sweep)];
    return vectors.flatMap((vector) => {
        const offset = vector.clone().subtract(centre);
        const turned = centre.clone();
        turned.x += offset.x * cos - offset.y * sin;
        turned.y += offset.x * sin + offset.y * cos;
        return turned.toLngLat().slice(0, 2);
    });
}

export const rectangleEditModel: EditModel<RectangleElement> = {
    controlPoints: rectangleControlPoints,

    applyHandle(element, controlPoint, lngLat, snapshot) {
        if (controlPoint.kind === "rotate") {
            const ring = rotateRing(
                snapshot ?? element.geometry.getAllPos(),
                controlPoint.position,
                lngLat,
            );
            if (ring) (element.geometry as PolygonGeometry).setAllPos(ring);
            return;
        }

        const i = controlPoint.index ?? 0;
        const geo = element.geometry;
        const reference = snapshot ?? geo.getAllPos();
        const corners = positionPairs(reference);
        if (corners.length < 4) return;

        const opp = (i + 2 + 4) % 4; // Opposite corner index.
        const left = (opp - 1 + 4) % 4; // Left of opp.
        const right = (opp + 1 + 4) % 4; // Right of opp.
        const [v0, v1, v2] = [left, opp, right].map((index) =>
            MercatorVector.fromLngLat(corners[index]),
        );

        const c = MercatorVector.fromLngLat([lngLat[0], lngLat[1]]);
        const u = v0.clone().subtract(v1);
        const v = v2.clone().subtract(v1);
        const rel = c.clone().subtract(v1);
        const projOnU = u.clone().scale(u.dot(rel) / u.dot(u));
        const projOnV = v.clone().scale(v.dot(rel) / v.dot(v));

        const st = v1.clone();
        const w = st.clone().add(projOnU);
        const x = st.clone().add(projOnV);

        const positions = (
            turf.rewind(
                turf.polygon([
                    [
                        w.toLngLat().slice(0, 2),
                        st.toLngLat().slice(0, 2),
                        x.toLngLat().slice(0, 2),
                        [lngLat[0], lngLat[1]],
                        w.toLngLat().slice(0, 2),
                    ],
                ]),
            ) as GeoJSON.Feature<GeoJSON.Polygon>
        ).geometry.coordinates[0]
            .flat()
            .slice(0, -2);

        (geo as PolygonGeometry).setAllPos(positions);
    },

    hitTest(element, screen, lngLat, project, tolerancePx): EditIntent {
        const cp = hitControlPoint(rectangleControlPoints(element), screen, project, tolerancePx);
        if (cp) return { kind: "handle", controlPoint: cp };
        if (hitBody(element, screen, lngLat, project, tolerancePx)) return { kind: "body" };
        return { kind: "none" };
    },

    invalidation: "vertex",
};
