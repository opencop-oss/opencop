// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { createHistoryStore } from "./history.store";
import { SYMBOL_HISTORY_STORAGE_KEY } from "./history.util";

export const useSymbolHistoryStore = createHistoryStore(SYMBOL_HISTORY_STORAGE_KEY);

export function useSymbolHistory() {
    return useSymbolHistoryStore((s) => s);
}
