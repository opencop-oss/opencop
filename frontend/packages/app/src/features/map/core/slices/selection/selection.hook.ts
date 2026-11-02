// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useShallow } from "zustand/react/shallow";
import { useMapStore } from "../../map.store";
import type { SelectionActions, SelectionState } from "./selection.slice";

export const useSelectionState = <T>(selector: (state: SelectionState) => T): T =>
    useMapStore(selector);

export const useSelectionActions = (): SelectionActions =>
    useMapStore(
        useShallow((state) => ({
            select: state.select,
            resetSelection: state.resetSelection,
            bumpDismiss: state.bumpDismiss,
        })),
    );
