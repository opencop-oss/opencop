// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useState } from "react";
import { useDisplayPreferences } from "../preferences/DisplayPreferencesContext.tsx";
import { formatCoord, parseCoordinate, type CoordFormat, type LatLng } from "@opencop/utils";

function formatLatLng(latLng: LatLng, format: CoordFormat): string {
    return formatCoord(latLng.lat, latLng.lng, format);
}

/**
 * State for a coordinate text input in the operator's coordinate format. While
 * unfocused it shows `defaultValue` (so it follows a drag on the map); while
 * focused it shows what the operator typed, and calls `onChange` with each
 * position that parses.
 */
export function useCoordinateInput(defaultValue?: LatLng, onChange?: (v: LatLng) => void) {
    const { coordFormat: format } = useDisplayPreferences();
    const [isFocused, setIsFocused] = useState(false);
    const [editValue, setEditValue] = useState("");
    const [error, setError] = useState(false);

    // When not focused, derive display value from prop (tracks map drags in real-time)
    // When focused, use the user's typed value
    const displayValue = isFocused
        ? editValue
        : defaultValue
          ? formatLatLng(defaultValue, format)
          : "";

    function handleFocus() {
        // On focus, initialize edit value from current prop
        setEditValue(defaultValue ? formatLatLng(defaultValue, format) : "");
        setError(false);
        setIsFocused(true);
    }

    function handleBlur() {
        setIsFocused(false);
        setError(false);
    }

    function handleChange(e: React.SyntheticEvent<HTMLInputElement>) {
        const inputValue = e.currentTarget.value;
        setEditValue(inputValue);

        const parsed = parseCoordinate(inputValue);
        if (!parsed) {
            setError(true);
            return;
        }

        setError(false);
        onChange?.(parsed);
    }

    return {
        value: displayValue,
        error,
        onChange: handleChange,
        onFocus: handleFocus,
        onBlur: handleBlur,
    };
}
