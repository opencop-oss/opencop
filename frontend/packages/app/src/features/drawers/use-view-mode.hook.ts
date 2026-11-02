// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useCallback, useState } from "react";

export function useViewMode() {
    const [mode, setMode] = useState<"view" | "edit">("view");
    // Stable identities: callers pass these straight through to child props. `setMode` is already stable.
    const toView = useCallback(() => setMode("view"), []);
    const toEdit = useCallback(() => setMode("edit"), []);
    return { mode, toView, toEdit };
}
