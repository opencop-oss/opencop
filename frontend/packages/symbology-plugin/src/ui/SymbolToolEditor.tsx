// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useCallback, useMemo, type ReactElement } from "react";
import { SIDC } from "@opencop/symbology";
import { Element, IconStyle, Symbol } from "@opencop/elements";
import { useToolSession } from "@opencop/tools";
import { NonSidcNotice } from "./NonSidcNotice";
import { SymbolEditor } from "./SymbolEditor";
import { readSymbologyParams } from "./symbology-tools";

/**
 * The symbol tool's toolbar editor — symbology's own binding of {@link SymbolEditor}
 * to the element being drawn.
 *
 * Registered by symbology as the `editor` on its `symbolTool` descriptor, so the
 * toolbar renders it without knowing anything about symbols. This collapses the
 * two stacked toolbar-side adapters that previously wrapped this editor from the
 * other side of the feature boundary.
 *
 * The active SIDC lives in the session's opaque `params` bag — symbology's own
 * state, same as the graphic tool's. It used to live in `SymbolDefaults.sidc`, a
 * mutable global inside `@opencop/elements` that the websocket decode path also
 * read, which made a wire event with no symbol render as whatever the operator
 * last picked.
 *
 * Because `symbolTool` now builds its element *from* those params, a newly drawn
 * symbol already carries the right code and there is nothing to seed after the
 * fact — which is what removed the previous "initialize each element once"
 * effect and its tracking ref.
 */
export function SymbolToolEditor(): ReactElement {
    const { draft, params: toolParams, setParams, invalidate } = useToolSession();
    // The symbol tool produces into the element store, so its draft is an element.
    const element = draft instanceof Element ? draft : null;

    const code = element && Symbol.is(element) ? element.getCode() : undefined;

    // Prefer the element being drawn/edited; otherwise the tool's current SIDC.
    // Memoised so the fallback doesn't mint a fresh SIDC on every render.
    const defaultValue = useMemo(
        () =>
            code ? SIDC.fromString(code) : (readSymbologyParams(toolParams).sidc ?? SIDC.default()),
        [code, toolParams],
    );

    const handleSymbolChange = useCallback(
        (sidc: SIDC) => {
            setParams({ ...toolParams, sidc });

            if (!element || !Symbol.is(element)) return;
            const target = element;
            target.setCode(sidc.toString());
            // The style carries size/angle only — the icon is resolved from
            // `code` at render time — so re-derive it from the current style
            // rather than from the SIDC.
            const style = target.getStyle();
            target.setStyle(new IconStyle({ size: style.getSize(), angle: style.getAngle() }));
            invalidate(target, "getIcon");
        },
        [element, toolParams, setParams, invalidate],
    );

    // Some other scheme owns this code slot (media icons, today), so the SIDC
    // editor can't meaningfully edit it. Decided from symbology's own domain — is
    // this shaped like an SIDC — not by naming another subject's scheme.
    if (code && !SIDC.isSidcString(code)) return <NonSidcNotice />;

    return <SymbolEditor defaultValue={defaultValue} onChange={handleSymbolChange} />;
}
