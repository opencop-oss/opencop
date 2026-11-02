// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useState } from "react";

interface NumericInputDetails {
    value: string;
    valueAsNumber: number;
}

interface FocusChangeDetails {
    focused: boolean;
}

/**
 * Focus/derive-from-prop pattern for a numeric field bound to a live, externally mutable
 * value (e.g. geometry read off an `Element` that can also change from outside, such as a
 * vertex dragged on the map): shows the live value while unfocused, and a local editable
 * draft while focused. Sibling to {@link useCoordinateInput} for non-coordinate numeric fields.
 *
 * @param value The current live value, already converted to display units.
 * @param onChange Called with the parsed entry (display units) on every valid keystroke.
 * @param format Renders a value for display. Defaults to plain `toString()`.
 */
export function useNumericInput(
    value: number,
    onChange: (value: number) => void,
    format: (value: number) => string = (v) => v.toString(),
) {
    const [isFocused, setIsFocused] = useState(false);
    const [editValue, setEditValue] = useState("");

    function handleFocus() {
        setEditValue(format(value));
        setIsFocused(true);
    }

    function handleBlur() {
        setIsFocused(false);
    }

    function handleChange(details: NumericInputDetails) {
        setEditValue(details.value);
        if (isNaN(details.valueAsNumber) || details.valueAsNumber < 0) return;
        onChange(details.valueAsNumber);
    }

    return {
        value: isFocused ? editValue : format(value),
        onValueChange: handleChange,
        onFocusChange: (details: FocusChangeDetails) => {
            if (details.focused) handleFocus();
            else handleBlur();
        },
    };
}
