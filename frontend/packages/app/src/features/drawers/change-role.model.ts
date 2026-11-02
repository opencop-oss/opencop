// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/** A member row shown in the bulk change-role dialog's table. */
export interface ChangeRoleRow {
    id: string;
    /** Primary label, e.g. the username. */
    label: string;
    /** Current role label, shown alongside the name. */
    role: string;
}

/** An alert shown in the change-role dialog. */
export interface ChangeRoleAlert {
    message: string;
}

/** Validation for the currently-checked selection in the change-role dialog. */
export interface ChangeRoleAlerts {
    /** Soft caution (yellow) alert shown at the bottom — e.g. "you're downgrading owners". */
    warning?: ChangeRoleAlert;
    /**
     * Rows in violation (e.g. this change would remove the last owner). Each is
     * flagged with a warning indicator + `tooltip` on its icon, and Apply is
     * disabled while any exist. Resolving it (e.g. keeping one owner) clears it.
     */
    blocked?: { rowIds: string[]; tooltip: string };
}
