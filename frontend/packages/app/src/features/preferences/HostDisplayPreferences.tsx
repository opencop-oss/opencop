// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useMemo, type ReactNode } from "react";
import { DisplayPreferencesProvider, type DisplayPreferences } from "@opencop/ui";
import { useCoordFormat } from "~/features/coordinates/CoordFormatContext";
import { useMeasurementSystem } from "~/features/measurements/use-measurement-system.hook";
import { useMapSettingsStore } from "~/features/map/map-settings.store";

/**
 * Provides the operator's display preferences to contributed UI, which reads them
 * with `useDisplayPreferences` from `@opencop/ui`. The values come from the host
 * settings that own them. Must sit inside `CoordFormatProvider`.
 */
export function HostDisplayPreferences({ children }: { children: ReactNode }) {
    const { system } = useMeasurementSystem();
    const { format, setFormat } = useCoordFormat();
    const angleUnit = useMapSettingsStore((state) => state.angleUnit);
    const angleOrientation = useMapSettingsStore((state) => state.angleOrientation);
    const setAngleUnit = useMapSettingsStore((state) => state.setAngleUnit);

    const preferences = useMemo<DisplayPreferences>(
        () => ({
            measurementSystem: system,
            coordFormat: format,
            setCoordFormat: setFormat,
            angleUnit,
            setAngleUnit,
            angleOrientation,
        }),
        [system, format, setFormat, angleUnit, setAngleUnit, angleOrientation],
    );

    return <DisplayPreferencesProvider value={preferences}>{children}</DisplayPreferencesProvider>;
}
