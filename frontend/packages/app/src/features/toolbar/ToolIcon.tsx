// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { TbAlertTriangle } from "react-icons/tb";
import type { ReactElement } from "react";
import { ContributionBoundary } from "~/features/contributions/ContributionBoundary";
import { ownerOfToolId } from "~/features/plugin-manager/plugin-tool-id";
import type { ToolDescriptor } from "~/features/contributions/tool-registry";
import { toolOrigin } from "@opencop/contributions/containment";

/**
 * A tool's icon, contained.
 *
 * The icon is a contributed component like the editor and the popover content —
 * it is bound to the plugin context at registration and may call hooks — but it
 * renders inside the toolbar's own row, where a throw unwinds past every other
 * tool. One bad icon must not empty the toolbar.
 *
 * The fallback is a visible warning glyph rather than nothing: the button keeps
 * its `aria-label` either way, so an empty one would be a target with no
 * indication anything is wrong. A glyph swap is invisible to a screen reader
 * though, so the caller is told through `onFallback` and adds the non-visual
 * half — see `NativeToolButton`. The glyph itself is `aria-hidden`: it is
 * decorative once the state is announced, and the button's name must stay the
 * tool's name so the control does not rename itself under the operator.
 */
export function ToolIcon({
    descriptor,
    onFallback,
}: {
    descriptor: ToolDescriptor;
    onFallback?: () => void;
}): ReactElement {
    return (
        <ContributionBoundary
            origin={toolOrigin(ownerOfToolId(descriptor.id), descriptor.id, "icon")}
            fallback={<TbAlertTriangle aria-hidden />}
            onFallback={onFallback}
        >
            <descriptor.icon />
        </ContributionBoundary>
    );
}
