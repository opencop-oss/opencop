// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useShallow } from "zustand/react/shallow";
import { useMapStore } from "../../map.store";
import type { ViewActions, ViewState } from "./view.slice";

export const useViewState = <T>(selector: (state: ViewState) => T): T => useMapStore(selector);

export const useViewActions = (): ViewActions =>
    useMapStore(
        useShallow((state) => ({
            setViewState: state.setViewState,
            flyTo: state.flyTo,
            jumpTo: state.jumpTo,
            fitBounds: state.fitBounds,
        })),
    );
