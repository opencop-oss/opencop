// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { createContext, use, useCallback, useMemo, useState, type ReactNode } from "react";
import type { CoordFormat } from "@opencop/utils";
import { getSavedFormat, saveFormat } from "./coord-format.storage";

interface CoordFormatContextType {
    format: CoordFormat;
    setFormat: (format: CoordFormat) => void;
}

const CoordFormatContext = createContext<CoordFormatContextType | null>(null);

export function useCoordFormat(): CoordFormatContextType {
    const ctx = use(CoordFormatContext);
    if (!ctx) throw new Error("useCoordFormat must be used within CoordFormatProvider");
    return ctx;
}

export function CoordFormatProvider({ children }: { children: ReactNode }) {
    const [coordFormat, setCoordFormat] = useState<CoordFormat>(getSavedFormat);

    const setFormat = useCallback((f: CoordFormat) => {
        setCoordFormat(f);
        saveFormat(f);
    }, []);

    const value = useMemo(() => ({ format: coordFormat, setFormat }), [coordFormat, setFormat]);

    return <CoordFormatContext value={value}>{children}</CoordFormatContext>;
}
