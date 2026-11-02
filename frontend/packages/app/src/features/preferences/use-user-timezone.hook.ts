// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useQuery } from "@tanstack/react-query";
import { preferencesQueries } from "./preferences.queries";
import { detectTimezone } from "./timezone.util";

export function useUserTimezone(): string {
    const { data: preferences } = useQuery(preferencesQueries.current());
    return preferences?.timezone ?? detectTimezone();
}
