// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * `@opencop/tools` — the **tools framework**: the contract a tool is declared
 * against, the pure draw grammars it is built from, and the pure
 * resolution of its own defaults.
 *
 * A **leaf**: it depends on `@opencop/elements`, turf and
 * math.gl — plus React and lodash for the session scope only, and the types of
 * `@opencop/scene` and `@opencop/utils` for how a session product looks — and on nothing
 * host-side or plugin-side. A native subject and a plugin
 * declare a tool against the same declarations rather than against two that have
 * to agree, and both import this package **directly** — `@opencop/plugin-sdk`
 * deliberately relays no contract.
 *
 * What is here is the API and its primitives; what is *not* here is any
 * concrete tool. A `ToolDescriptor` — the thing with an id, an icon, a shortcut
 * and a place in the toolbar — belongs to whichever subject ships it (an in-app
 * plugin, a package plugin), never to the framework. The built-in **grammars**
 * are the pure input loops a tool draws with: `lineGrammar` and friends as they
 * are, or `createSequentialGrammar` and the other factories configured for a
 * subject's own product. The built-in **kits** (`kits.polygon`, …) bundle a
 * grammar with its edit model for a subject to declare as its descriptor's `draw`.
 *
 * It holds contracts and pure functions only — **no registries and no FSM**. The
 * tool registry, the session store, the lifecycle runner,
 * the edit interpreter, and all input arbitration stay host-owned. The one piece
 * of React here is `ToolSessionScope` and its two hooks: the editor contract is
 * read as a hook, and a context declared in this leaf is what lets a native
 * editor and a plugin editor read it without importing the host.
 *
 * `ToolError` is a runtime class, exported as a value and `instanceof`-checked by
 * the host, and the session scope's contexts must be the instance the host
 * provides, so this package joins the shared-singleton set when plugins move to
 * Native Federation — and `react` must be shared as one instance too. That is a
 * recorded consequence of putting them here, not an accident.
 */

export {
    ToolError,
    DEFAULT_TOOL_ORDER,
    INTERACTION_TOOL_GROUP,
    resolveGrammar,
    type ToolParams,
    type LabelUnits,
    type ToolDescriptor,
    type DrawKit,
    type ToolLocation,
    type InputSpec,
    type SliderSpec,
    type SwatchSpec,
    type NumberSpec,
    type SelectSpec,
    type WeightSpec,
    type PatternSpec,
    type StyleSpec,
    type CompletionHost,
} from "./tool";

export {
    ELEMENT_STORE_ID,
    SESSION_STORE_ID,
    producedElementType,
    productionError,
    styleError,
    type ProductTarget,
} from "./product-target";

export {
    SEQUENTIAL_DOUBLE_CLICK_MAX,
    defaultDrawHints,
    completesOnDoubleClick,
    resolveDrawHints,
    type DrawHint,
    type DrawHintPhase,
    type DrawHintState,
    type DrawHints,
    type HostHintOptions,
} from "./draw-hints";

export type {
    DraftRefs,
    DrawGrammar,
    DrawInput,
    DrawPointerEvent,
    DrawPointerEventType,
    DrawSubscription,
} from "./draw-grammar";

export {
    createParametricGrammar,
    createPointGrammar,
    createSequentialGrammar,
    createTraceGrammar,
    type GrammarConfig,
    type ParametricGrammarConfig,
    type ParametricPhase,
    type PointGrammarConfig,
    type SequentialGrammarConfig,
    type TraceGrammarConfig,
} from "./grammars";

export type { ControlPoint, EditIntent, EditModel, ScreenProjector } from "./edit-model";

export { dist, pointToSegmentDistance, positionPairs, setVertex } from "./screen-math";

export { vertexControlPoints, hitControlPoint, hitBody } from "./edit-helpers";

export {
    angleGrammar,
    arrowGrammar,
    circleGrammar,
    circlePhases,
    lineGrammar,
    pointGrammar,
    polygonGrammar,
    rectangleGrammar,
    rectanglePhases,
    traceGrammar,
} from "./shape-grammars";

export { circleEditModel } from "./circle.model";
export { rectangleEditModel } from "./rectangle.model";

export { kits } from "./kits";

export type { ToolSession, ToolPhase } from "./tool-session";
export {
    ToolSessionScope,
    useToolSession,
    useToolSessionRevision,
    type ToolSessionScopeProps,
} from "./ToolSessionContext";

export { MercatorVector } from "./MercatorVector";
