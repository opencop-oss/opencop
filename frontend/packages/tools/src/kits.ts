// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * Draw kits: the draw half of a tool as one reusable record — how the pointer
 * becomes geometry (`grammar`), how the finished product is changed by drags
 * (`editModel`), and what the operator is told while drawing (`hints`).
 *
 * A tool declares its kit as `draw`, using a built-in kit as-is or spreading
 * one and naming the part it replaces:
 *
 * ```ts
 * ctx.registerTool({ id: "polygonTool", …, produces: { store: ELEMENT_STORE_ID, type: "polygon" }, draw: kits.polygon });
 * ctx.registerTool({ id: "zoneTool", …, produces, draw: { ...kits.polygon, hints: zoneHints } });
 * ```
 *
 * How a product looks is never part of a kit; the renderer registered for the
 * tool's product type owns that, while drawing and after. A kit that omits
 * `editModel` gets the host's generic vertex-drag model, and one that omits
 * `hints` gets its grammar's default hints.
 */
import { circleEditModel } from "./circle.model";
import type { EditModel } from "./edit-model";
import { rectangleEditModel } from "./rectangle.model";
import {
    angleGrammar,
    arrowGrammar,
    circleGrammar,
    lineGrammar,
    pointGrammar,
    polygonGrammar,
    rectangleGrammar,
    traceGrammar,
} from "./shape-grammars";
import type { DrawKit } from "./tool";

/** The built-in kits, one per built-in shape. */
export const kits = {
    point: { grammar: pointGrammar },
    line: { grammar: lineGrammar },
    arrow: { grammar: arrowGrammar },
    angle: { grammar: angleGrammar },
    polygon: { grammar: polygonGrammar },
    // Each model is typed for its own element; widening it is sound because a kit's
    // edit model only ever edits what that kit's grammar creates.
    circle: { grammar: circleGrammar, editModel: circleEditModel as EditModel },
    rectangle: { grammar: rectangleGrammar, editModel: rectangleEditModel as EditModel },
    trace: { grammar: traceGrammar },
} as const satisfies Record<string, DrawKit>;
