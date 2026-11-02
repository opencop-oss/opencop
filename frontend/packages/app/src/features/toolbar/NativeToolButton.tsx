// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { IconButton, VisuallyHidden } from "@chakra-ui/react";
import { Tooltip } from "@opencop/ui";
import { useId, useState, type ComponentProps, type ReactElement } from "react";
import { useTranslation } from "react-i18next";
import { ToolIcon } from "./ToolIcon";
import type { ToolDescriptor } from "~/features/contributions/tool-registry";

export interface NativeToolButtonProps {
    descriptor: ToolDescriptor;
    /** The tool's session is the active one — rendered pressed. */
    highlighted: boolean;
    disabled: boolean;
    /**
     * Historical mode: the button stays named after the tool (the accessible
     * name must not change under the operator) while its tooltip explains why
     * drawing is off instead of naming the unusable tool.
     */
    historical: boolean;
    onSelect: (id: string) => void;
    size?: ComponentProps<typeof IconButton>["size"];
}

/**
 * A native draw tool's toolbar button: clicking it hands the descriptor id to
 * `onSelect`, which enters (or leaves) that tool's session. Shared by every
 * piece of chrome that renders registry descriptors — the tool picker, its
 * overflow popover and the interaction-mode cluster — so a registered tool
 * looks and announces the same wherever the registry places it. Resolves its
 * own label from the descriptor (`labelKey`, else `toolbar.<id>`).
 */
export function NativeToolButton({
    descriptor,
    highlighted,
    disabled,
    historical,
    onSelect,
    size = { base: "compact", lg: "md" },
}: NativeToolButtonProps): ReactElement {
    const { t } = useTranslation();
    // A broken icon falls back to a warning glyph, which says nothing to a screen
    // reader. Describe the button rather than renaming it: the accessible *name*
    // must stay the tool's name, or the control renames itself under the operator.
    const [iconFailed, setIconFailed] = useState(false);
    const unavailableId = useId();

    const label = t((descriptor.labelKey ?? `toolbar.${descriptor.id}`) as never, descriptor.label);
    // Tool labels keep the toolbar's uppercase style; the sentence reads better un-uppercased.
    const tip = historical ? t("toolbar.drawDisabledHistorical") : label;

    return (
        <IconButton
            size={size}
            border="none"
            active="iconToggle"
            data-active={highlighted ? "" : undefined}
            aria-label={label}
            aria-pressed={highlighted}
            aria-describedby={iconFailed ? unavailableId : undefined}
            onClick={() => onSelect(descriptor.id)}
            disabled={disabled}
        >
            <Tooltip content={tip} uppercase={!historical}>
                <ToolIcon descriptor={descriptor} onFallback={() => setIconFailed(true)} />
            </Tooltip>
            {iconFailed && (
                <VisuallyHidden id={unavailableId}>{t("toolbar.toolUnavailable")}</VisuallyHidden>
            )}
        </IconButton>
    );
}
