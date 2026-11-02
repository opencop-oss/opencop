// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { registerEscapeClaimant } from "~/features/map/interaction-runtime/arbitration/escape-claimants";
import { clearAreaSelection, hasAreaSelection } from "./area-selection.store";

/**
 * The selection feature's install-time act: the area selection as an Escape
 * claimant — the `area-selection` slot of the runtime's Escape channel, the
 * last party Escape reaches. Active while a working set stands; dismissed by
 * the one verb every channel uses (`clearAreaSelection`), which also drops the
 * events navigation's references to the list that is gone. The runtime never
 * learns what that does.
 *
 * Registered directly with arbitration rather than through the contribution
 * context: naming a slot in the runtime's closed order is not something a
 * subject contributes, so the member is not on that surface.
 *
 * Returns the detach; the composition root does not call it today (native
 * installers live for the app's lifetime), but the shape is the one an
 * HMR-safe root will need.
 */
export function installSelection(): () => void {
    return registerEscapeClaimant("area-selection", {
        isActive: hasAreaSelection,
        dismiss: clearAreaSelection,
    });
}
