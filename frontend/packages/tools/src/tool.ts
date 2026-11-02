// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ComponentType } from "react";
import type { DraftObject, Element } from "@opencop/elements";
import type { Primitive } from "@opencop/scene";
import type { AngleOrientation, AngleUnit, MeasurementSystem } from "@opencop/utils";
import type { DrawGrammar } from "./draw-grammar";
import type { DrawHints } from "./draw-hints";
import type { EditModel } from "./edit-model";
import type { ProductTarget } from "./product-target";

/**
 * The tool contract: what a subject (native feature or plugin) declares to add a
 * toolbar tool. Native features register a `ToolDescriptor` into the app-owned
 * tool registry directly; a plugin declares the same shape from `install(ctx)`.
 * There is one type, not a narrow plugin variant widened by a host adapter.
 *
 * **Declarative and host-neutral.** Everything a tool declares is data or pure
 * functions over cursor + element + geometry — no `MapManager`, no store, no
 * pipeline. That includes the *interaction* half of the contract below (the
 * shortcut, the cursor, control-point editing, what completing the draft
 * means, what Escape means over the finished product): a tool says what its
 * geometry means and how its session looks, and the host's interaction
 * runtime decides who owns input and does the binding. That separation is
 * what lets the whole contract live in a leaf a plugin imports directly.
 */

/**
 * Where a tool places itself: a region of the host toolbar, or outside it
 * entirely. The three toolbar regions name *regions* rather than whatever
 * currently occupies them, so they stay meaningful if the host rearranges its
 * own contents (and read correctly under RTL, unlike left/right) — each is
 * prefixed `topBar` so the fourth, non-toolbar value reads unambiguously by
 * contrast.
 *
 * - `topBarStart`  — with the drawing-tool cluster
 * - `topBarCenter` — with the coordinate input
 * - `topBarEnd`    — after the coordinate input, before the mapbar toggles
 * - `external`     — no toolbar button; the tool stays in the registry so
 *   `activate(id)` still works from wherever its own entry point lives
 *   (a panel's own button today, any future non-toolbar surface later).
 *   Native-only for now: the plugin loader refuses a plugin tool that
 *   declares it, since no plugin surface exists yet to activate it from.
 *
 * The mapbar itself is deliberately NOT a location: its toggles belong to a
 * different contribution than a toolbar tool (a mapbar panel kind, removed
 * for lack of a consumer, returns with the first plugin panel that needs it —
 * together with the reactive state it reads). Adding a location value later
 * is additive and non-breaking.
 */
export type ToolLocation = "topBarStart" | "topBarCenter" | "topBarEnd" | "external";

/** Sort key applied to a tool that doesn't declare an `order`. */
export const DEFAULT_TOOL_ORDER = 100;

/** Opaque, tool-owned parameters a params-derived tool builds its grammar from. */
export type ToolParams = Readonly<Record<string, unknown>>;

/** The conventions labels are written in. */
export interface LabelUnits {
    readonly angle: AngleUnit;
    readonly angleOrientation: AngleOrientation;
    readonly measurement: MeasurementSystem;
}

/**
 * Thrown by a params-derived grammar factory when no drawable tool exists for the given
 * params. The host catches it and refuses the activation (or leaves an armed
 * session with no draw bindings) rather than crashing the map. Part of the
 * contract, so a feature/plugin tool factory
 * can signal an unusable selection without the host knowing why it was unusable.
 */
export class ToolError extends Error {
    /**
     * Why no tool was resolved, in the subject's own vocabulary — this package
     * cannot enumerate the reasons without naming one subject's domain.
     */
    readonly reason: string;

    /** @param detail the offending input, if the subject has one worth naming. */
    constructor(reason: string, detail?: string) {
        super(detail ? `No draw tool for ${detail} (${reason})` : `No draw tool (${reason})`);
        this.name = "ToolError";
        this.reason = reason;
    }
}

// ─── Style inputs (the active tool's formatting bar) ──────────────────────

export interface InputSpec<T> {
    defaultValue: T;
    onChange: (element: Element, v: T) => void;
}

export interface SliderSpec extends InputSpec<number> {
    type: "slider";
    min: number;
    max: number;
    step: number;
}

export interface SwatchSpec extends InputSpec<string> {
    type: "swatch";
}

export interface NumberSpec extends InputSpec<number> {
    type: "number";
    min?: number;
    max?: number;
    step?: number;
    suffix?: string;
}

export interface SelectSpec extends InputSpec<string> {
    type: "select";
    items: readonly string[];
}

export interface WeightSpec extends InputSpec<number> {
    type: "weight";
    min: number;
    max: number;
    step: number;
}

export interface PatternSpec extends InputSpec<string> {
    type: "pattern";
    items: readonly string[];
}

/** One formatting-bar item shown while a tool is active. */
export interface StyleSpec {
    label: string;
    /** i18n key used for display. Falls back to `label` if not set. */
    labelKey?: string;
    input: SliderSpec | NumberSpec | SwatchSpec | SelectSpec | WeightSpec | PatternSpec;
    icon?: ComponentType;
}

// ─── Interaction semantics ────────────────────────────────────────────────

/**
 * The host capabilities a completion hook may use — spatial queries only, no
 * store handles.
 *
 * @remarks provisional — audited before SDK publication.
 */
export interface CompletionHost {
    /** Elements with at least one vertex inside the closed polygon. */
    pickInsidePolygon(lngLatVertices: readonly [number, number][]): Element[];
    /**
     * Hand a set of elements to the host as the operator's current selection,
     * with the boundary they were gathered from so the map can draw it.
     *
     * The host owns what a selection *is* — which panel lists it, how it is
     * exported, what happens to it when an element is deleted — so a tool that
     * gathers one hands it over and keeps nothing. Passing an empty array
     * clears the selection.
     *
     * The boundary is the region the tool selected from, in `[lng, lat]`, open
     * (no repeated closing vertex). It is presentation the host attaches to the
     * selection's lifetime, not geometry the tool retains.
     */
    selectElements(elements: readonly Element[], boundary: readonly [number, number][]): void;
}

/**
 * The `topBarStart`-location group drawn by the interaction-mode chrome: tools whose
 * product is not a persisted element. The host decides how the group is
 * drawn; a tool joins it by declaring `group: INTERACTION_TOOL_GROUP`. A plugin
 * tool cannot join it — the host groups plugin tools under its own key at
 * registration.
 */
export const INTERACTION_TOOL_GROUP = "interaction";

// ─── The unified tool descriptor ──────────────────────────────────────────

/**
 * The draw half of a tool: how the pointer becomes geometry, how the finished
 * product is changed by drags, and what the operator is told meanwhile. A
 * subject reuses a built-in kit (`kits.polygon`, …) as-is, or spreads one and
 * replaces a part. How the product looks is never part of a kit — the renderer
 * for the tool's product type owns that, while drawing and after.
 */
export interface DrawKit {
    /**
     * The draw's grammar — a built-in one (`polygonGrammar`, or a factory such as
     * `createSequentialGrammar`), or one the tool implements. A function of params
     * for a tool whose draw is derived at activation time.
     */
    readonly grammar: DrawGrammar | ((params: ToolParams) => DrawGrammar);
    /**
     * Declarative control-point editing for the product. Omitted ⇒ the host's
     * generic vertex-drag model.
     */
    readonly editModel?: EditModel;
    /**
     * The instructions shown while drawing and while the product is edited.
     * Omitted ⇒ the grammar's defaults; a function receives those
     * defaults to extend or replace; `false` shows none.
     */
    readonly hints?: DrawHints;
}

/**
 * One toolbar tool. The single shape both native features and plugins register
 * — the app-owned tool registry stores exactly this, and a plugin `install`
 * contributes exactly this (the host only decorates a plugin's entry: namespaces
 * its id, groups it, and binds its `content`/`editor` to the plugin context).
 *
 * A descriptor is one of two flavours, by which capability it carries:
 * - **draw tool** — has `draw`; activating it enters a session that runs
 *   its kit's grammar (a freehand trace is a grammar like any other).
 * - **popover tool** — has `content`; clicking it opens the popover.
 *
 * **Every interaction field is optional and defaulted**, so a plain shape tool
 * declares none of them and still gets today's behavior: a crosshair cursor
 * while drawing, Escape aborting the draft then leaving the tool, the draft
 * completing into an edit session, and generic vertex-drag editing. Who owns
 * the pointer, what right-click means, when the map's pan and double-click
 * zoom are held off, whether the host holds the product in the element store
 * while it is drawn and edited, and where a tool may activate are not a tool's
 * to declare — the host derives them
 * from the session's kind, the grammar and the store in `produces`.
 */
export interface ToolDescriptor {
    /** Stable id (e.g. `"lineTool"`, or a plugin's namespaced id). */
    readonly id: string;
    /** Display fallback; the i18n key defaults to `toolbar.<id>` unless `labelKey` is set. */
    readonly label: string;
    /** Explicit i18n key, when it doesn't follow the `toolbar.<id>` convention. */
    readonly labelKey?: string;
    readonly icon: ComponentType;
    /** Where the tool places itself. Defaults to `"topBarStart"`. */
    readonly location?: ToolLocation;
    /** Sort key within a location; lower first. Ties keep registration order. */
    readonly order?: number;
    /**
     * Placement key within a location. The host decides which piece of its
     * toolbar chrome draws each group; a tool that declares none joins the
     * location's default cluster. Grouping is placement only — no renderer
     * draws dividers between groups.
     */
    readonly group?: string;
    /**
     * Where this tool's product lives, as two string identifiers: `store`, the
     * id of the store the product belongs to, and `type`, the **type key** the
     * product carries within that store (`"polygon"`, `"<subject>:<name>"`) — a
     * string, not a TypeScript type. The renderer that draws the product is
     * registered under the same `(store, type)` pair.
     *
     * - **The element store** (`ELEMENT_STORE_ID`, with an element type key): the
     *   product is a persisted element. From first placement to Save or Cancel
     *   the host holds it in the element store, locked against live updates,
     *   then commits it or rolls it back; and it resolves this tool for any
     *   element of that type edited later. The grammar must draft an `Element`; any other draft
     *   is reported as a tool defect and the draw refused.
     * - **A registered store:** the product stays on the session; the store
     *   never holds it. While drawing, the store's `draftOf` turns the draft
     *   into one of its objects; the tool's own flow saves the product.
     * - **The host's session store** (`SESSION_STORE_ID`): a product with no store
     *   to save into yet, drawn as the draft itself and dropped when the session
     *   ends. Its type keys are namespaced (`"<subject>:<name>"`; a plugin's
     *   subject is its plugin id).
     *
     * Omitted for a tool that draws nothing (a popover tool).
     */
    readonly produces?: ProductTarget;
    /**
     * Declarative style inputs shown in the formatting bar while active. Each
     * input writes the product's element style, so only a tool whose product is
     * an element may declare them; the host refuses any other tool that does.
     */
    readonly style?: readonly StyleSpec[];
    /**
     * How this tool draws and how its product is edited — a {@link DrawKit}. A
     * built-in kit (`kits.polygon`, …) is used as-is or spread with one part
     * replaced. Its presence is what makes this a draw tool.
     */
    readonly draw?: DrawKit;
    /**
     * How the product looks while the session holds it, for a tool whose product
     * is held by the session store (`SESSION_STORE_ID`). A product bound for any
     * other store is drawn by that store.
     *
     * @provisional — Until tools are abstracted a bit more.
     */
    readonly mapProduct?: (
        draft: DraftObject,
        units: { measurement: MeasurementSystem },
    ) => Primitive[];
    /** Editor mounted in the toolbar while this tool is active. */
    readonly editor?: ComponentType;
    /** Popover body (popover tools). */
    readonly content?: ComponentType;
    /**
     * Domain bookkeeping after an element this tool edits is saved — a subject
     * recording what was just committed in its own history, for example. It
     * fires for the elements this tool edits — its product's type, less any
     * another owner's renderer draws — whichever session saved the element.
     */
    readonly onCommit?: (element: Element) => void;
    /** Suppress the generic formatting bar (tool supplies its own editor). */
    readonly hideStyleBar?: boolean;

    // ─── Interaction ──────────────────────────────────────────────────────

    /**
     * Single-letter keyboard shortcut ("p", "l", …). The host owns gating:
     * refused in historical mode, over an edit and over an in-flight draw; a
     * tool that is merely waiting for its first click is preempted.
     */
    readonly shortcut?: string;

    /**
     * CSS cursor the canvas shows while this tool's session is drawing
     * (waiting for the first click included). An edit session derives its
     * cursor from what is under the pointer instead. Default `"crosshair"`.
     */
    readonly cursor?: string;

    /**
     * What completing the draft means for a tool whose product is not
     * something to edit. Called synchronously inside the host's `complete`
     * command with the finished draft; the tool does its domain work and says
     * whether the session leaves (`"exit"`) or re-arms for another attempt
     * (`"re-arm"`). The draft is the tool's to read inside the hook and is
     * released when it returns. Omitted ⇒ the draft completes into an EDIT
     * session over the product — held in the element store when that is where
     * the product lives, on the session otherwise.
     *
     * @remarks provisional — audited before SDK publication.
     */
    readonly onComplete?: (product: DraftObject, host: CompletionHost) => "exit" | "re-arm";

    /**
     * What Escape does over this tool's finished product (its EDIT session).
     * `"cancel"` (default) cancels the edit: an element held in the element
     * store reverts or is discarded, a product on the session is dropped and
     * the tool left.
     * `"restart"` drops the product and re-arms the tool with the same params
     * (a re-take loop). Escape while drawing is not a tool's to
     * declare — the host aborts an in-flight draft and stays armed, or leaves
     * a tool that is only waiting.
     */
    readonly onEscapeInEdit?: "cancel" | "restart";
}

/**
 * Resolve a descriptor's draw grammar, passing params for params-derived tools.
 * Returns `undefined` for a content-only (popover) tool — those never draw.
 */
export function resolveGrammar(
    descriptor: ToolDescriptor,
    params: ToolParams,
): DrawGrammar | undefined {
    const grammar = descriptor.draw?.grammar;
    if (!grammar) return undefined;
    return typeof grammar === "function" ? grammar(params) : grammar;
}
