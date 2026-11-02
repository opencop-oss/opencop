// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { CoordFormat } from "@opencop/utils";

export const COORD_FORMAT_KEY = "opencop-coord-format";

export function getSavedFormat(): CoordFormat {
    const saved = localStorage.getItem(COORD_FORMAT_KEY);
    if (saved === "DD" || saved === "DMS" || saved === "DDM" || saved === "MGRS" || saved === "UTM")
        return saved;
    return "DD";
}

export function saveFormat(format: CoordFormat): void {
    localStorage.setItem(COORD_FORMAT_KEY, format);
}
