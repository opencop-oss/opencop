// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { VscSymbolConstant } from "react-icons/vsc";
import { Renderer, SIDC } from "@opencop/symbology";
import type { Element, SymbolElement } from "@opencop/elements";
import { cachedImage } from "@opencop/ui";
import { ELEMENT_STORE_ID } from "@opencop/tools";
import type { ToolDescriptor, ToolParams } from "@opencop/tools";
import type { ElementMapper } from "@opencop/contributions";
/**
 * The registration surface symbology actually uses.
 *
 * Structural on purpose, naming neither the host's `ContributionContext` nor the
 * plugin's `PluginInstallContext` — both satisfy it. That is what let this one
 * function move from the app's composition root to the plugin's `install(ctx)`
 * with no signature change and no host import.
 *
 * It declares no `editModel`: symbology's element types edit under the host's
 * generic vertex-drag model.
 */
interface SymbologyInstallContext {
    registerTool(tool: ToolDescriptor): unknown;
    registerElementMapper<E extends Element, S>(mapper: ElementMapper<E, S>): unknown;
}
import { SymbolToolEditor } from "./SymbolToolEditor";
import { readSymbologyParams, symbolGrammarFromSIDC } from "./symbology-tools";
import { MilSymbolMapper, type MilSymbolState } from "../map/MilSymbolMapper";
import { useSymbolHistoryStore } from "./symbol-history.store";

const SYMBOLOGY_GROUP = "symbology";

export function installSymbology(ctx: SymbologyInstallContext): void {
    ctx.registerTool({
        id: "symbolTool",
        label: "symbol",
        // The host's `toolbar.<id>` fallback reads the host catalog; this label
        // lives in the plugin's own namespace, so name the key.
        labelKey: "symbology:tools.symbol",
        icon: VscSymbolConstant,
        group: SYMBOLOGY_GROUP,
        order: 70,
        produces: { store: ELEMENT_STORE_ID, type: "symbol" },
        style: [],
        // Params-derived: the SIDC lives in symbology's
        // own `toolParams` slice, so the element is created with its final code
        // instead of being seeded afterwards by the editor.
        draw: {
            grammar: (params: ToolParams) =>
                symbolGrammarFromSIDC(readSymbologyParams(params).sidc ?? SIDC.default()),
        },
        editor: SymbolToolEditor,
        // Record the saved symbol in symbology's own recents. This used to be a
        // hardcoded `if (element.type === "symbol")` branch inside the toolbar's
        // generic save path; it now belongs to the tool that owns the domain.
        onCommit: (element) => {
            const code = element.getCode();
            if (!code) return;
            useSymbolHistoryStore
                .getState()
                .addRecent(code, element.getModifiers() as Record<string, string>);
        },
    });

    // The events-panel thumbnail is the SIDC preview image, decoded once per code;
    // `renderPreview` returns null for an unrenderable code, so the consumer shows
    // its own fallback.
    const sidcPreview = (element: Element) => {
        const url = Renderer.renderPreview(element.getCode() ?? "");
        return url ? cachedImage(url) : null;
    };

    const symbol = new MilSymbolMapper();
    ctx.registerElementMapper<SymbolElement, MilSymbolState>({
        type: symbol.type,
        selector: symbol.selector,
        map: (element, size) => symbol.map(element, size),
        preview: sidcPreview,
        textSetting: { labelKey: "mapSettings.textLabels.symbol", order: 70 },
    });
}
