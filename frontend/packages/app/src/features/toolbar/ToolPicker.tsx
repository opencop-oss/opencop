// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { OverflowContainer, OverflowPopover } from "@opencop/ui";
import type { ReactElement, ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { NativeToolButton } from "./NativeToolButton";
import { PluginToolButton } from "./PluginToolButton";
import { INTERACTION_TOOL_GROUP } from "@opencop/tools";
import {
    useToolsForLocationExcluding,
    type ToolDescriptor,
} from "~/features/contributions/tool-registry";
import { useToolButtonState } from "./use-tool-button-state.hook";

export function ToolPicker(): ReactElement {
    const { t } = useTranslation();
    const {
        toolsDisabled,
        activeDrawId: activeToolId,
        isHistorical,
        handleToolClick,
    } = useToolButtonState();
    const descriptors = useToolsForLocationExcluding("topBarStart", INTERACTION_TOOL_GROUP);

    function renderTool(
        descriptor: ToolDescriptor,
        opts?: { compact?: boolean; onAfterSelect?: () => void },
    ): ReactNode {
        return descriptor.content ? (
            <PluginToolButton key={descriptor.id} descriptor={descriptor} />
        ) : (
            <NativeToolButton
                key={descriptor.id}
                descriptor={descriptor}
                size={opts?.compact ? "xs" : undefined}
                highlighted={activeToolId === descriptor.id}
                disabled={toolsDisabled}
                historical={isHistorical}
                onSelect={(id) => {
                    handleToolClick(id);
                    opts?.onAfterSelect?.();
                }}
            />
        );
    }

    return (
        <OverflowContainer
            items={descriptors}
            renderItem={(descriptor) => renderTool(descriptor)}
            renderOverflow={(hidden) => (
                <OverflowPopover
                    label={t("toolbar.moreTools", "More tools")}
                    renderItems={(close) =>
                        hidden.map((descriptor) =>
                            renderTool(descriptor, { compact: true, onAfterSelect: close }),
                        )
                    }
                />
            )}
            gap="0"
            alignItems="center"
            flex="1"
        />
    );
}
