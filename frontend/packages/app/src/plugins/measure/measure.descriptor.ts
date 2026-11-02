// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * The measure tool as ONE descriptor: registry data over the same grammar
 * factories every other tool uses, each drafting a {@link Measurement} rather
 * than an element; a product in the host's session store (so the runtime never
 * stores or locks the measurement, and the host draws it with
 * `measureRenderer`); and the proven handle math as its edit model. The finished measurement is an
 * EDIT session the runtime holds no custody over — the same session kind a
 * just-drawn shape gets, minus the store.
 */
import { PiRuler } from "react-icons/pi";
import {
    INTERACTION_TOOL_GROUP,
    ToolError,
    circlePhases,
    createParametricGrammar,
    createSequentialGrammar,
    type DrawGrammar,
    type EditModel,
    type ToolDescriptor,
    type ToolParams,
} from "@opencop/tools";
import { MeasureEditor } from "./MeasureEditor";
import { SESSION_STORE_ID } from "@opencop/tools";
import { measureHandlesModel } from "./measure-handles.model";
import { measureProductPrimitives } from "./measure.mapper";
import {
    MEASURE_PRODUCT_TYPE,
    circleMeasurement,
    lineMeasurement,
    polygonMeasurement,
    type Measurement,
} from "./measurement.model";
import { MEASURE_TOOL_ID, resolveMeasureShape } from "./measure-session";

const lineMeasureGrammar = createSequentialGrammar({ min: 2, create: lineMeasurement });
const polygonMeasureGrammar = createSequentialGrammar({ min: 3, create: polygonMeasurement });
const circleMeasureGrammar = createParametricGrammar<Measurement>({
    create: circleMeasurement,
    phases: circlePhases,
});

function measureGrammar(params: ToolParams): DrawGrammar {
    switch (resolveMeasureShape(params)) {
        case "line":
            return lineMeasureGrammar;
        case "polygon":
            return polygonMeasureGrammar;
        case "circle":
            return circleMeasureGrammar;
        default:
            // Armed with no shape picked — the session idles without draw
            // bindings until the editor's shape toggle re-arms it.
            throw new ToolError("no measure shape selected");
    }
}

export const measureDescriptor: ToolDescriptor = {
    id: MEASURE_TOOL_ID,
    label: "measure",
    labelKey: "toolbar.measure",
    icon: PiRuler,
    location: "topBarStart",
    group: INTERACTION_TOOL_GROUP,
    order: 20,
    // The model is typed for a measurement; widening it is sound because it only
    // ever edits what measure's own grammars draft.
    draw: { grammar: measureGrammar, editModel: measureHandlesModel as EditModel },
    produces: { store: SESSION_STORE_ID, type: MEASURE_PRODUCT_TYPE },
    mapProduct: measureProductPrimitives,
    editor: MeasureEditor,
    // Escape over the finished measurement drops it and re-arms the same
    // shape — the one place a tool overrides the edit default (cancel), so a
    // measurement can be re-taken without leaving the tool.
    onEscapeInEdit: "restart",
};
