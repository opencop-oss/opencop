// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useDropNotifications, type ShowToastOptions } from "./DropNotificationsContext";

/** Narrow hook for ephemeral toasts — decouples consumers from the full notification context. */
export function useToast(): { showToast: (opts: ShowToastOptions) => void } {
    return { showToast: useDropNotifications().showToast };
}
