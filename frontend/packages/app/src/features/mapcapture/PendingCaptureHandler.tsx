// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useEffect, useRef } from "react";
import { useSearchParams } from "react-router";
import { useMapbarStore } from "~/features/mapbar/mapbar.store";

/** Opens the captures panel when navigating from User Preferences Initial View link. */
export function PendingCaptureHandler() {
    const [searchParams, setSearchParams] = useSearchParams();
    const openPanel = useMapbarStore((s) => s.openPanel);
    const hasHandledRef = useRef(false);

    useEffect(() => {
        // Only handle once per component mount
        if (hasHandledRef.current) return;

        const openCapture = searchParams.get("openCapture");

        if (openCapture) {
            hasHandledRef.current = true;
            // Open captures panel
            openPanel("captures");

            // Clear URL param
            setSearchParams((prev) => {
                prev.delete("openCapture");
                return prev;
            });
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []); // Empty deps - only run on mount

    return null;
}
