// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { createContext, use, type ReactNode } from "react";
import type { AngleOrientation, AngleUnit, CoordFormat, MeasurementSystem } from "@opencop/utils";

/**
 * How the operator reads values on screen. The host owns each preference and
 * provides the current values; contributed UI reads them here instead of
 * importing the host.
 */
export interface DisplayPreferences {
    readonly measurementSystem: MeasurementSystem;
    readonly coordFormat: CoordFormat;
    readonly setCoordFormat: (format: CoordFormat) => void;
    readonly angleUnit: AngleUnit;
    readonly setAngleUnit: (unit: AngleUnit) => void;
    readonly angleOrientation: AngleOrientation;
}

const DisplayPreferencesContext = createContext<DisplayPreferences | null>(null);

/** Provides the display preferences to everything below it. The host mounts it. */
export function DisplayPreferencesProvider({
    value,
    children,
}: {
    value: DisplayPreferences;
    children: ReactNode;
}) {
    return <DisplayPreferencesContext value={value}>{children}</DisplayPreferencesContext>;
}

/** The operator's display preferences. Throws outside a `DisplayPreferencesProvider`. */
export function useDisplayPreferences(): DisplayPreferences {
    const preferences = use(DisplayPreferencesContext);
    if (!preferences) {
        throw new Error("useDisplayPreferences must be used within a DisplayPreferencesProvider");
    }
    return preferences;
}
