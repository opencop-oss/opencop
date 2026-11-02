// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { IconButton, Popover, Portal, useDisclosure } from "@chakra-ui/react";
import { Tooltip } from "@opencop/ui";
import type { ReactElement } from "react";
import { ErrorBoundaryWrapper } from "~/features/errors/ErrorBoundaryWrapper";
import { ToolIcon } from "./ToolIcon";
import type { ToolDescriptor } from "~/features/contributions/tool-registry";
import { toolOrigin } from "@opencop/contributions/containment";
import { ownerOfToolId } from "~/features/plugin-manager/plugin-tool-id";
import { reportContributionFailure } from "~/features/contributions/contribution-guard";

/**
 * A plugin tool button: an icon whose popover renders the plugin's `content`
 * (already bound to the plugin context at registration, so the host renders it
 * directly). Used for plugin tools in every slot — the toolbar renders these
 * from the same unified registry native tools live in. Mirrors the toolbar's own
 * popover inputs (`ToolInput`). Renders nothing for a descriptor without
 * `content` (a native tool), so callers can pass any descriptor safely.
 */
export function PluginToolButton({
    descriptor,
}: {
    descriptor: ToolDescriptor;
}): ReactElement | null {
    const { open, onClose, onToggle } = useDisclosure();
    const Content = descriptor.content;
    if (!Content) return null;

    return (
        <Popover.Root
            open={open}
            // Controlled popover: Escape (and outside-click) reach us via
            // onOpenChange, not internal state — without this the popover can't be
            // dismissed by keyboard. onInteractOutside is kept as the explicit
            // outside-click path; both route to the same close.
            onOpenChange={(d) => {
                if (!d.open) onClose();
            }}
            onInteractOutside={onClose}
            positioning={{ placement: "bottom-start" }}
        >
            <Popover.Trigger asChild>
                <IconButton
                    size={{ base: "compact", lg: "md" }}
                    active={open ? "icon" : "iconHover"}
                    border="none"
                    aria-label={descriptor.label}
                    onClick={onToggle}
                >
                    <Tooltip content={descriptor.label}>
                        <ToolIcon descriptor={descriptor} />
                    </Tooltip>
                </IconButton>
            </Popover.Trigger>
            <Portal>
                <Popover.Positioner>
                    <Popover.Content bg="slate.popover" w="fit-content" minW="0" p="0">
                        <Popover.Body p="1">
                            {/* Isolate a throwing plugin popover so it degrades to a
                                fallback instead of taking down the whole toolbar, and
                                attribute the throw as every contained failure is. */}
                            <ErrorBoundaryWrapper
                                componentName={descriptor.label}
                                onError={(error) =>
                                    reportContributionFailure(
                                        toolOrigin(
                                            ownerOfToolId(descriptor.id),
                                            descriptor.id,
                                            "content",
                                        ),
                                        error,
                                    )
                                }
                            >
                                <Content />
                            </ErrorBoundaryWrapper>
                        </Popover.Body>
                    </Popover.Content>
                </Popover.Positioner>
            </Portal>
        </Popover.Root>
    );
}
