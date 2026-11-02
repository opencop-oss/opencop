// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import {
    DefaultTextBoxStyle,
    Element,
    TextBox,
    TextBoxStyle,
    type DraftObject,
} from "@opencop/elements";
import {
    createParametricGrammar,
    rectanglePhases,
    type DrawGrammar,
    type ParametricPhase,
    type ToolParams,
} from "@opencop/tools";
import { boxFrame } from "./box-geometry.util";
import { growToHoldText } from "./box-text.util";

/**
 * The element type this plugin owns.
 *
 * A literal rather than `TextBox.type`, so loading this module reads nothing off
 * `@opencop/elements`. Anything reached at module scope throws in a test that
 * mocks that package partially, and the plugin is in the graph of every test
 * that mounts the app. The annotation fails the build if the two ever diverge.
 */
export const TEXTBOX_TYPE: typeof TextBox.type = "textbox";

/** The tool's id, before the host namespaces it with the plugin id. */
export const TEXTBOX_TOOL_ID = "textboxTool";

/** The slice of the session's opaque params this plugin owns. */
interface TextBoxParams {
    text?: string;
    textColor?: string;
    textSize?: number;
}

export function readTextBoxParams(params: ToolParams): TextBoxParams {
    return {
        text: typeof params["text"] === "string" ? params["text"] : undefined,
        textColor: typeof params["textColor"] === "string" ? params["textColor"] : undefined,
        textSize: typeof params["textSize"] === "number" ? params["textSize"] : undefined,
    };
}

/** Defaults with the operator's pending choices applied, so a new box is created with them. */
export function styleFromParams(params: TextBoxParams): TextBoxStyle {
    const style = DefaultTextBoxStyle.clone();
    if (params.text !== undefined) style.setText(params.text);
    if (params.textColor !== undefined) style.setTextColor(params.textColor);
    if (params.textSize !== undefined) style.setTextSize(params.textSize);
    return style;
}

/** Text height as a fraction of the box's own height. */
const AUTO_TEXT_FRACTION = 0.12;

/**
 * Size the text off the box it sits in. The box's metre height already encodes
 * the zoom it was drawn at, so a box dragged to the same apparent size gets the
 * same apparent text at any zoom — and text can never start too big to fit.
 */
export function applyAutoTextSize(object: DraftObject): void {
    if (!(object instanceof Element) || !TextBox.is(object)) return;
    // The box's own height, not its bounding box: those differ once it is
    // rotated, and the text should follow the box rather than its extent.
    const frame = boxFrame(object.geometry.getAllPos());
    if (!frame) return;

    // Only dirty on a real change: this runs on every mousemove of the final
    // draw phase, and `Reactive.dirty` notifies listeners synchronously.
    const style = object.getStyle();
    const next = frame.height * AUTO_TEXT_FRACTION;
    if (style.getTextSize() !== next) style.setTextSize(next);
}

/** The rectangle phases, with the last one sizing the text and the box to each other. */
function autoSizedPhases(): readonly ParametricPhase[] {
    const last = rectanglePhases.length - 1;
    return rectanglePhases.map((phase, index) =>
        index < last
            ? phase
            : {
                  ...phase,
                  preview(element, cursor) {
                      phase.preview(element, cursor);
                      applyAutoTextSize(element);
                      if (element instanceof Element && TextBox.is(element)) {
                          growToHoldText(element);
                      }
                  },
              },
    );
}

/** Makes the finished box read upright; only on completion, since the draw phases re-read the first edge each move. */
function upright(grammar: DrawGrammar): DrawGrammar {
    return {
        ...grammar,
        bind: (input, draft) =>
            grammar.bind(input, {
                ...draft,
                onComplete(object) {
                    if (object instanceof Element && TextBox.is(object)) {
                        object.geometry.setAllPos(
                            TextBox.uprightPositions(object.geometry.getAllPos()),
                        );
                    }
                    draft.onComplete(object);
                },
            }),
    };
}

/**
 * The host's rectangle grammar, producing a textbox.
 *
 * The text always follows the box while it is being drawn, even if a size was
 * picked beforehand: a size chosen at one zoom means nothing at another, and
 * text too small to see reads as the map being broken rather than as a size
 * that needs changing. The operator sets the size once there is text to judge
 * it against, and from then on it is theirs.
 */
export const textBoxGrammar = (params: ToolParams) =>
    upright(
        createParametricGrammar({
            create: (cursor) =>
                TextBox.create({
                    positions: cursor,
                    style: styleFromParams(readTextBoxParams(params)),
                }),
            phases: autoSizedPhases(),
        }),
    );
