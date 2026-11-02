// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Divider } from "@opencop/ui";
import type { ReactElement } from "react";
import type { ToolLocation } from "@opencop/tools";
import { PluginToolButton } from "./PluginToolButton";
import { useToolsForLocation } from "~/features/contributions/tool-registry";

/**
 * The plugin-tool group for a non-`topBarStart` location (`topBarCenter` /
 * `topBarEnd`). Native tools all live at `topBarStart` (rendered by
 * `ToolPicker`), so everything here is a plugin tool. Reads the unified tool
 * registry — empty in a vanilla build, so this renders nothing and the
 * toolbar is byte-identical to vanilla until a plugin loads.
 */
type PluginToolsLocation = Extract<ToolLocation, "topBarCenter" | "topBarEnd">;

export function PluginTools({ location }: { location: PluginToolsLocation }): ReactElement | null {
    const tools = useToolsForLocation(location);
    if (tools.length === 0) return null;

    return (
        <>
            <Divider orientation="vertical" borderWidth="1px" height="1.5em" />
            {tools.map((descriptor) => (
                <PluginToolButton key={descriptor.id} descriptor={descriptor} />
            ))}
        </>
    );
}
