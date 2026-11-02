// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ElementMapper } from "@opencop/contributions";
import type { Element } from "@opencop/elements";
import { ELEMENT_STORE_ID, kits, type ToolDescriptor } from "@opencop/tools";
import { MdTextFields } from "react-icons/md";
import { TextBoxElementMapper } from "./TextBoxElementMapper";
import { TextBoxToolEditor } from "./TextBoxToolEditor";
import { textBoxEditModel } from "./textbox-edit-model";
import { TEXTBOX_TOOL_ID, TEXTBOX_TYPE, textBoxGrammar } from "./textbox-tools";
import { textBoxStyleSpecs } from "./textbox-style-specs";

/** Structural, so the host context and the SDK's both satisfy it. */
interface TextBoxInstallContext {
    registerTool(tool: ToolDescriptor): unknown;
    registerElementMapper<E extends Element = Element, S = undefined>(
        mapper: ElementMapper<E, S>,
    ): unknown;
}

const TEXTBOX_GROUP = "textbox";

/** Owns the `textbox` element type: tool, editor and element mapper. */
export function installTextBox(ctx: TextBoxInstallContext): void {
    ctx.registerTool({
        id: TEXTBOX_TOOL_ID,
        label: "textbox",
        // The derived key would be `toolbar.textboxTool`; name the real one.
        labelKey: "textbox:toolbar.textbox",
        icon: MdTextFields,
        group: TEXTBOX_GROUP,
        order: 66,
        produces: { store: ELEMENT_STORE_ID, type: TEXTBOX_TYPE },
        style: textBoxStyleSpecs,
        // The rectangle kit, with a corner drag that keeps the box's first edge
        // in slots 0 and 1 and re-grows the box to hold its text.
        draw: { ...kits.rectangle, editModel: textBoxEditModel, grammar: textBoxGrammar },
        editor: TextBoxToolEditor,
    });

    // No `preview`: the thumbnail helper the shapes use is host source, so the
    // events panel shows its placeholder instead. No `textSetting` either — the
    // content is the object, not a label about it, so it has no Map Settings
    // toggle and survives labels being turned off.
    ctx.registerElementMapper(new TextBoxElementMapper());
}
