// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { type DateTime } from "luxon";
import { createContext, use } from "react";

export interface DateTimeContextType {
    date: DateTime;
    setDate: (arg: DateTime | ((prev: DateTime) => DateTime)) => void;
    min: DateTime;
    max: DateTime;
}

export const DateTimeContext = createContext<DateTimeContextType | null>(null);

export function useDateTimeContext(): DateTimeContextType {
    const context = use(DateTimeContext);
    if (!context) {
        throw new Error("useDateTimeContext must be used within a DateTimeProvider");
    }
    return context;
}
