// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Checkbox, VisuallyHidden } from "@chakra-ui/react";
import { TacticalText } from "../typography/TacticalText";

export interface DefaultCheckboxProps {
    checked: boolean;
    /** Visible label text. Pass `label=""` when the surrounding context (e.g. a table row label) already names the control; pair with `aria-label` for accessibility. */
    label: string;
    onCheckedChange: (checked: boolean) => void;
    disabled?: boolean;
    "aria-label"?: string;
}

/** Checkbox with tactical styling matching the panel card pattern. Supports an optional `disabled` state and a blank `label` when context supplies its own. */
export function DefaultCheckbox({
    checked,
    label,
    onCheckedChange,
    disabled,
    "aria-label": ariaLabel,
}: DefaultCheckboxProps) {
    return (
        <Checkbox.Root
            size="sm"
            cursor={disabled ? "not-allowed" : "pointer"}
            checked={checked}
            disabled={disabled}
            aria-label={ariaLabel}
            onClick={(e) => e.stopPropagation()}
            onKeyDown={(e) => e.stopPropagation()}
            onCheckedChange={(e) => onCheckedChange(!!e.checked)}
        >
            <Checkbox.HiddenInput aria-label={ariaLabel} />
            <Checkbox.Control
                bg="bg.elevatedLight"
                borderColor="slate.icon.primary"
                borderWidth="1.5px"
                cursor={disabled ? "not-allowed" : "pointer"}
            >
                <Checkbox.Indicator />
            </Checkbox.Control>
            <Checkbox.Label>
                {label ? (
                    <TacticalText
                        fontSize="2xs"
                        textTransform="uppercase"
                        letterSpacing="0.08em"
                        color={checked ? "active" : "fg.muted"}
                    >
                        {label}
                    </TacticalText>
                ) : (
                    <VisuallyHidden>{ariaLabel}</VisuallyHidden>
                )}
            </Checkbox.Label>
        </Checkbox.Root>
    );
}
