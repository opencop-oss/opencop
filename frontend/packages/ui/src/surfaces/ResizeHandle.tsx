// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Center } from "@chakra-ui/react";
import { useRef, type KeyboardEvent } from "react";

const KEYBOARD_STEP_PX = 16;
const KEYBOARD_PAGE_PX = 48;

// Shared style applied to the pip on hover, active, and focus.
const pipActive = { bg: "fg.subtle" };

export interface ResizeHandleProps {
    /**
     * Called with the pixel delta on every drag tick and keyboard press.
     * May receive ±Infinity for Home/End snap-to-extreme; callers must clamp.
     */
    onResize: (delta: number) => void;
    /** Accessible label (aria-label). */
    label: string;
    /** Current position for aria-valuenow. Must be within [valueMin, valueMax]. */
    valueNow: number;
    /** Lower bound for aria-valuemin. */
    valueMin: number;
    /** Upper bound for aria-valuemax. */
    valueMax: number;
    /**
     * When true, dragging down and ArrowDown shrink the primary region.
     * Use for separators that control the panel below them (the primary region
     * grows when the separator moves up).
     */
    invertDelta?: boolean;
    /** Hit-target height and pip thickness. Defaults to "sm". */
    size?: "sm" | "md";
}

const sizeMap = {
    sm: { handleH: "8px", pipH: "2px" },
    md: { handleH: "12px", pipH: "3px" },
} as const;

export function ResizeHandle({
    onResize,
    label,
    valueNow,
    valueMin,
    valueMax,
    invertDelta = false,
    size = "sm",
}: ResizeHandleProps) {
    const prevYRef = useRef(0);

    const handleKeyDown = (e: KeyboardEvent<HTMLDivElement>) => {
        const step = invertDelta ? -KEYBOARD_STEP_PX : KEYBOARD_STEP_PX;
        const page = invertDelta ? -KEYBOARD_PAGE_PX : KEYBOARD_PAGE_PX;
        switch (e.key) {
            case "ArrowDown":
                e.preventDefault();
                onResize(step);
                break;
            case "ArrowUp":
                e.preventDefault();
                onResize(-step);
                break;
            case "PageDown":
                e.preventDefault();
                onResize(page);
                break;
            case "PageUp":
                e.preventDefault();
                onResize(-page);
                break;
            case "Home":
                e.preventDefault();
                onResize(invertDelta ? Number.POSITIVE_INFINITY : Number.NEGATIVE_INFINITY);
                break;
            case "End":
                e.preventDefault();
                onResize(invertDelta ? Number.NEGATIVE_INFINITY : Number.POSITIVE_INFINITY);
                break;
        }
    };

    const { handleH, pipH } = sizeMap[size];

    return (
        <Center
            role="separator"
            aria-orientation="horizontal"
            aria-label={label}
            aria-valuenow={valueNow}
            aria-valuemin={valueMin}
            aria-valuemax={valueMax}
            tabIndex={0}
            h={handleH}
            w="full"
            flexShrink={0}
            cursor="row-resize"
            // touchAction="none" is required — without it the browser claims a
            // touch-drag as a scroll and pointermove stops firing mid-gesture.
            touchAction="none"
            _hover={{ "& > div": pipActive }}
            _active={{ "& > div": pipActive }}
            _focusVisible={{
                "& > div": pipActive,
                outlineWidth: "2px",
                outlineStyle: "solid",
                outlineColor: "brand.focusRing",
                outlineOffset: "2px",
            }}
            onPointerDown={(e) => {
                e.currentTarget.setPointerCapture(e.pointerId);
                prevYRef.current = e.clientY;
                document.body.style.cursor = "row-resize";
            }}
            onPointerMove={(e) => {
                if (!e.currentTarget.hasPointerCapture(e.pointerId)) return;
                const raw = e.clientY - prevYRef.current;
                prevYRef.current = e.clientY;
                onResize(invertDelta ? -raw : raw);
            }}
            onPointerUp={(e) => {
                e.currentTarget.releasePointerCapture(e.pointerId);
                document.body.style.cursor = "";
            }}
            onKeyDown={handleKeyDown}
        >
            <Box
                w="48px"
                h={pipH}
                borderRadius="full"
                bg="fg.muted"
                transition="background-color 120ms"
            />
        </Center>
    );
}
