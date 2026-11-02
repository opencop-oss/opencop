// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { PointStyle, ShapeStyle, StrokeStyle, type Element } from "@opencop/elements";
import type { StrokePattern } from "@opencop/elements";

/**
 * The shapes' thumbnails: each drawer draws one element into a `size`×`size`
 * canvas from its geometry and style, and returns `false` when the element's
 * style is not the kind its type expects (or it has too few positions), so the
 * host shows its placeholder instead. {@link shapePreview} turns a drawer into a
 * shape renderer's `preview`.
 */

const PAD = 4;

/** A drawer: draws `element` into a `size`×`size` context, or returns `false`. */
export type ShapeDrawer = (
    ctx: CanvasRenderingContext2D,
    element: Element,
    size: number,
) => boolean;

/** A thumbnail's side, in CSS pixels: the stacked row's box. */
const PREVIEW_SIZE = 40;
/** Drawn at twice that, so the thumbnail stays sharp on dense screens. */
const PREVIEW_SCALE = 2;

/** `element`'s thumbnail drawn by `draw` into a new canvas, or `null` when it cannot draw it. */
export function shapePreview(draw: ShapeDrawer, element: Element): HTMLCanvasElement | null {
    const canvas = document.createElement("canvas");
    canvas.width = PREVIEW_SIZE * PREVIEW_SCALE;
    canvas.height = PREVIEW_SIZE * PREVIEW_SCALE;
    const ctx = canvas.getContext("2d");
    if (!ctx) return null;
    ctx.scale(PREVIEW_SCALE, PREVIEW_SCALE);
    return draw(ctx, element, PREVIEW_SIZE) ? canvas : null;
}

/** Geometry positions in Web Mercator, so a thumbnail matches the map's projection. */
function mercatorPoints(geometry: Element["geometry"]): [number, number][] {
    const pts: [number, number][] = [];
    for (let i = 0; i < geometry.numPositions; i++) {
        const coord = geometry.getMercatorCoordinate(i);
        pts.push([coord.x, coord.y]);
    }
    return pts;
}

function normalizePoints(pts: [number, number][], size: number): [number, number][] {
    const xs = pts.map((p) => p[0]);
    const ys = pts.map((p) => p[1]);
    const minX = Math.min(...xs);
    const maxX = Math.max(...xs);
    const minY = Math.min(...ys);
    const maxY = Math.max(...ys);
    const rangeX = maxX - minX || 1;
    const rangeY = maxY - minY || 1;
    const drawSize = size - PAD * 2;
    const scale = Math.min(drawSize / rangeX, drawSize / rangeY);
    const offsetX = PAD + (drawSize - rangeX * scale) / 2;
    const offsetY = PAD + (drawSize - rangeY * scale) / 2;
    return pts.map(([x, y]) => [offsetX + (x - minX) * scale, offsetY + (y - minY) * scale]);
}

function applyDash(ctx: CanvasRenderingContext2D, pattern: StrokePattern, weight: number): void {
    if (pattern.name === "dashed") ctx.setLineDash([weight * 3, weight * 2]);
    else if (pattern.name === "dotted") ctx.setLineDash([weight, weight * 2]);
    else ctx.setLineDash([]);
}

function tracePath(ctx: CanvasRenderingContext2D, pts: [number, number][]): void {
    ctx.beginPath();
    ctx.moveTo(pts[0][0], pts[0][1]);
    for (let i = 1; i < pts.length; i++) ctx.lineTo(pts[i][0], pts[i][1]);
}

export function drawPointPreview(
    ctx: CanvasRenderingContext2D,
    element: Element,
    size: number,
): boolean {
    const style = element.getStyle();
    if (!(style instanceof PointStyle)) return false;
    const r = Math.min(Math.max(style.getRadius() * 2, 4), size / 2 - 2);
    ctx.beginPath();
    ctx.arc(size / 2, size / 2, r, 0, Math.PI * 2);
    ctx.fillStyle = style.getColor();
    ctx.fill();
    ctx.strokeStyle = "rgba(255,255,255,0.3)";
    ctx.lineWidth = 1;
    ctx.stroke();
    return true;
}

export function drawCirclePreview(
    ctx: CanvasRenderingContext2D,
    element: Element,
    size: number,
): boolean {
    const style = element.getStyle();
    if (!(style instanceof ShapeStyle)) return false;
    const weight = Math.min(style.getStrokeWeight(), 3);
    ctx.lineWidth = weight;
    applyDash(ctx, style.getStrokePattern(), weight);
    ctx.beginPath();
    ctx.arc(size / 2, size / 2, (size - PAD * 2) / 2, 0, Math.PI * 2);
    ctx.fillStyle = style.getFillColor();
    ctx.fill();
    ctx.strokeStyle = style.getStrokeColor();
    ctx.stroke();
    return true;
}

/** A filled, closed outline: polygons, rectangles and ellipses. */
export function drawClosedPathPreview(
    ctx: CanvasRenderingContext2D,
    element: Element,
    size: number,
): boolean {
    const style = element.getStyle();
    if (!(style instanceof ShapeStyle) || element.geometry.numPositions < 2) return false;
    const pts = normalizePoints(mercatorPoints(element.geometry), size);
    const weight = Math.min(style.getStrokeWeight(), 3);
    ctx.lineWidth = weight;
    applyDash(ctx, style.getStrokePattern(), weight);
    tracePath(ctx, pts);
    ctx.closePath();
    ctx.fillStyle = style.getFillColor();
    ctx.fill();
    ctx.strokeStyle = style.getStrokeColor();
    ctx.stroke();
    return true;
}

/** An open stroke: lines and angles. */
export function drawOpenPathPreview(
    ctx: CanvasRenderingContext2D,
    element: Element,
    size: number,
): boolean {
    const style = element.getStyle();
    if (!(style instanceof StrokeStyle) || element.geometry.numPositions < 2) return false;
    const pts = normalizePoints(mercatorPoints(element.geometry), size);
    const weight = Math.min(style.getWeight(), 3);
    ctx.lineWidth = weight;
    applyDash(ctx, style.getPattern(), weight);
    tracePath(ctx, pts);
    ctx.strokeStyle = style.getColor();
    ctx.stroke();
    return true;
}

export function drawArrowPreview(
    ctx: CanvasRenderingContext2D,
    element: Element,
    size: number,
): boolean {
    const style = element.getStyle();
    if (!(style instanceof StrokeStyle) || element.geometry.numPositions < 2) return false;
    const pts = normalizePoints(mercatorPoints(element.geometry), size);
    const color = style.getColor();
    ctx.lineWidth = Math.min(style.getWeight(), 3);
    ctx.setLineDash([]);
    tracePath(ctx, pts);
    ctx.strokeStyle = color;
    ctx.stroke();
    const last = pts[pts.length - 1];
    const prev = pts[pts.length - 2];
    const angle = Math.atan2(last[1] - prev[1], last[0] - prev[0]);
    const headLen = 6;
    ctx.beginPath();
    ctx.moveTo(last[0], last[1]);
    ctx.lineTo(
        last[0] - headLen * Math.cos(angle - Math.PI / 6),
        last[1] - headLen * Math.sin(angle - Math.PI / 6),
    );
    ctx.lineTo(
        last[0] - headLen * Math.cos(angle + Math.PI / 6),
        last[1] - headLen * Math.sin(angle + Math.PI / 6),
    );
    ctx.closePath();
    ctx.fillStyle = color;
    ctx.fill();
    return true;
}
