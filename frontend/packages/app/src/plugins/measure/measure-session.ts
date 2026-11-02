// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * Measure's reading of its scoped session — what the measure editor shows and
 * how it changes the armed shape. Built on `useToolSession()`, the view the host
 * opens around the editor, so measure reads no host store.
 *
 * A finished measurement is an EDIT session the runtime holds no custody over
 * — the same session kind a just-drawn shape gets, minus the store.
 */
import { useCallback } from "react";
import { useToolSession, type ToolParams } from "@opencop/tools";
import { asMeasurement, type Measurement } from "./measurement.model";

export type MeasureShape = "circle" | "line" | "polygon";

export const MEASURE_TOOL_ID = "measureTool";

/** The shape a measure session arms with when its params name none. */
export const DEFAULT_MEASURE_SHAPE: MeasureShape = "line";

/**
 * Measure's reading of its own params. `shape` absent ⇒ the default — a plain
 * `activate(MEASURE_TOOL_ID)` from the toolbar arms Line, the same as every
 * other tool needing no params to start. `shape: null` ⇒ no shape: the
 * editor's toggle deselected it and the session waits without draw bindings
 * until a shape is picked again.
 */
export function resolveMeasureShape(params: ToolParams): MeasureShape | null {
    const shape = params.shape as MeasureShape | null | undefined;
    return shape === undefined ? DEFAULT_MEASURE_SHAPE : shape;
}

/** The shape armed for the next measurement, or null (a finished measurement is on display, or no shape picked). */
export function useMeasureShape(): MeasureShape | null {
    const { phase, params } = useToolSession();
    return phase === "draw" ? resolveMeasureShape(params) : null;
}

/** The measurement on display: the one being drawn, or the finished one; null otherwise. */
export function useMeasurement(): Measurement | null {
    return asMeasurement(useToolSession().draft);
}

/**
 * Toggle a shape: picking a shape re-arms with it (params restart — the
 * destroy-and-restart semantics, which also drops a finished measurement);
 * picking the active shape again clears it (armed, no shape).
 */
export function useToggleMeasureShape(): (shape: MeasureShape) => void {
    const { setParams } = useToolSession();
    const armed = useMeasureShape();
    return useCallback(
        (shape: MeasureShape) => setParams({ shape: armed === shape ? null : shape }),
        [setParams, armed],
    );
}
