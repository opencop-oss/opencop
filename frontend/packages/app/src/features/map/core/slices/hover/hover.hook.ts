// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useMapStore } from "../../map.store";
import type { HoverState } from "./hover.slice";

export const useHoverState = <T>(selector: (state: HoverState) => T): T => useMapStore(selector);
