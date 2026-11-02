// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Flex } from "@chakra-ui/react";
import type { ReactElement, ReactNode } from "react";
import { ToolSessionScope } from "@opencop/tools";
import { ContributionBoundary } from "~/features/contributions/ContributionBoundary";
import { ownerOfToolId } from "~/features/plugin-manager/plugin-tool-id";
import { getTool } from "~/features/contributions/tool-registry";
import { useActiveDescriptorId } from "~/features/map/interaction-runtime/session/selectors";
import { useScopedToolSession, useScopedToolSessionRevision } from "./use-tool-session.hook";
import { toolOrigin } from "@opencop/contributions/containment";

/**
 * Where a tool's editor is rendered. The **host** decides placement: a subject
 * declares `editor` on its tool descriptor and the outlet mounts it here, in the
 * map's floating-chrome region, inside a container the app owns.
 *
 * A subject therefore contributes editor *content*, never a position. This is the
 * reason there is no portal seam in `@opencop/ui` any more — the editor is mounted
 * where it is meant to appear, so it has nothing to escape from. A plugin cannot
 * relocate itself into the app's layout, which is a boundary guarantee as much as
 * a layout one.
 *
 * ONE rule for every session kind: the active descriptor's `editor` renders
 * while its session is live — draw or edit (measure's editor arrives
 * by the same declaration as symbology's; no hardcoded branches).
 *
 * The editor reads its own tool's session: the outlet opens a `ToolSessionScope`
 * for the descriptor it mounts, so a native editor and a plugin editor read the
 * same scoped view and neither names a tool id to get it.
 *
 * The container is `flex="1"` so it claims the left half of the chrome row and
 * pushes the right-hand panels over, and `pointerEvents` stays off until an editor
 * opts in — the row above it is a click-through overlay on the map.
 */
export function ToolEditorOutlet(): ReactElement {
    const descriptorId = useActiveDescriptorId();
    const Editor = descriptorId ? getTool(descriptorId)?.editor : undefined;

    return (
        <Flex flex="1" zIndex={2000} pointerEvents="none">
            {/* An editor is contributed code the host mounts on the map. Without
                a boundary here the nearest one is `MapView`, so a throwing editor
                replaces the entire map; contained, it costs only the editor and
                the toolbar keeps working. `resetKeys` on the descriptor id means
                switching tools mounts the next editor cleanly. */}
            {Editor && descriptorId ? (
                <ContributionBoundary
                    origin={toolOrigin(ownerOfToolId(descriptorId), descriptorId, "editor")}
                >
                    <HostToolSessionScope toolId={descriptorId}>
                        <Editor />
                    </HostToolSessionScope>
                </ContributionBoundary>
            ) : null}
        </Flex>
    );
}

/**
 * The host's scoped session for `toolId`, opened to `children`. The editor
 * element arrives as `children` from the outlet, which does not re-render on a
 * geometry change, so a revision bump re-renders only what reads the revision.
 */
function HostToolSessionScope({
    toolId,
    children,
}: {
    toolId: string;
    children: ReactNode;
}): ReactElement {
    const session = useScopedToolSession(toolId);
    const revision = useScopedToolSessionRevision(toolId);
    return (
        <ToolSessionScope session={session} revision={revision}>
            {children}
        </ToolSessionScope>
    );
}
