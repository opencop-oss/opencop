// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { mergeRefs, Switch, Text, VStack, type SwitchRootProps } from "@chakra-ui/react";
import type { CSSProperties, ReactNode, Ref } from "react";
import { useEffect, useRef } from "react";

const OC_SWITCH_SHIELD_STYLE: CSSProperties = {
    flex: 1,
    alignSelf: "stretch",
    background: "transparent",
    border: 0,
    padding: 0,
    cursor: "default",
};

export interface OcSwitchProps {
    ref?: Ref<HTMLInputElement>;
    checked: boolean;
    label: ReactNode;
    onCheckedChange: (checked: boolean) => void;
    disabled?: boolean;
    /**
     * Full-width row with label on the left and switch on the right (`justify-content: space-between`).
     * Default true — matches settings/panel layouts. Set false for natural-width inline clusters.
     */
    fullWidth?: boolean;
    /** Optional description rendered below the row. */
    description?: ReactNode;
    /** Hide the label visually (kept for a11y / Playwright). For icon-only switches. */
    srOnlyLabel?: boolean;
    /** Visual variant. Use `"placeholder"` for non-interactive disabled toggles. */
    variant?: "placeholder";
    /** Color palette token. Defaults to `"cyan"`. Use `"brand"` in mapper-builder contexts. */
    colorPalette?: SwitchRootProps["colorPalette"];
    /** Override Switch.Root style props (e.g. `size`, `cursor`). */
    rootProps?: Omit<SwitchRootProps, "checked" | "disabled" | "onCheckedChange" | "colorPalette">;
}

export function OcSwitch({
    ref,
    checked,
    label,
    onCheckedChange,
    disabled,
    fullWidth = true,
    description,
    srOnlyLabel,
    variant,
    colorPalette = "cyan",
    rootProps,
}: OcSwitchProps) {
    const isPlaceholder = variant === "placeholder";
    const inputRef = useRef<HTMLInputElement>(null);
    // zag renders the hidden checkbox uncontrolled (`defaultChecked`) and re-syncs the DOM node only
    // when its own machine value changes. When the owner rejects a toggle (`checked` never moves) the
    // node stays flipped, and the next click is a no-op resync that swallows the interaction.
    useEffect(() => {
        const el = inputRef.current;
        if (el && el.checked !== checked) el.checked = checked;
    });
    const switchEl = (
        <Switch.Root
            w={fullWidth ? "full" : "auto"}
            justifyContent={fullWidth ? "space-between" : undefined}
            alignItems="center"
            gap="3"
            // Right inset reserves space for the focus ring on the control —
            // many of our panels (Collapsible.Content, settings dropdowns) clip
            // overflow at the right edge and would otherwise crop the ring.
            pr="1"
            colorPalette={colorPalette}
            checked={checked}
            disabled={disabled}
            cursor={isPlaceholder ? "not-allowed" : undefined}
            onCheckedChange={(details) => onCheckedChange(details.checked)}
            {...rootProps}
        >
            <Switch.Label srOnly={srOnlyLabel}>{label}</Switch.Label>
            {fullWidth && (
                // Switch.Root is a <label>, so any blank space inside it forwards
                // clicks to the input via HTML label activation. A non-interactive
                // spacer wouldn't help (preventDefault/stopPropagation don't suppress
                // label activation). Filling the middle with a transparent button —
                // interactive content per the HTML label spec — absorbs clicks so
                // only the visible label and the control toggle the switch.
                <button
                    type="button"
                    tabIndex={-1}
                    aria-hidden="true"
                    style={OC_SWITCH_SHIELD_STYLE}
                    onMouseDown={(e) => e.preventDefault()}
                />
            )}
            <Switch.HiddenInput ref={mergeRefs(inputRef, ref)} role="switch" />
            <Switch.Control
                outline={isPlaceholder ? "2px solid" : undefined}
                outlineColor={isPlaceholder ? "border" : undefined}
            >
                <Switch.Thumb />
            </Switch.Control>
        </Switch.Root>
    );

    if (description) {
        return (
            <VStack w={fullWidth ? "full" : "auto"} align="start" gap="1">
                {switchEl}
                <Text color="slate.text.primary" fontSize="sm">
                    {description}
                </Text>
            </VStack>
        );
    }

    return switchEl;
}
