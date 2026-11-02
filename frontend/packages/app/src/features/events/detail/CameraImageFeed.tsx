// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Center, Image, Spinner, Text, VisuallyHidden } from "@chakra-ui/react";
import { useCallback, useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { useSpinDelay } from "spin-delay";
import { isBlockedMixedContent } from "./attachment.util";

interface CameraImageFeedProps {
    url: string;
    style?: React.CSSProperties;
    /** Fill the parent box, letterboxed on black, instead of taking the feed's own height. */
    fill?: boolean;
}

type FeedState = "loading" | "playing" | "offline";

const CAMERA_LOAD_TIMEOUT_MS = 10_000;

/** Cameras stop delivering frames after ~20 s with no error event, so stay under that. */
const RECONNECT_INTERVAL_MS = 15_000;

/** Consecutive failures of the visible feed itself before it is called offline. */
const MAX_CONSECUTIVE_FAILURES = 3;

/** Grace period past the reconnect interval before a missed handover is flagged as possibly stale. */
const STALE_GRACE_MS = 10_000;

/** Reusing an identical URL may be a cache hit or a no-op. */
function withCacheBuster(url: string): string {
    const hashAt = url.indexOf("#");
    const base = hashAt === -1 ? url : url.slice(0, hashAt);
    const hash = hashAt === -1 ? "" : url.slice(hashAt);
    return `${base}${base.includes("?") ? "&" : "?"}_ocr=${Date.now()}${hash}`;
}

type Slots = [string | null, string | null];

/**
 * Plays an MJPEG feed in an `<img>`, which decodes `multipart/x-mixed-replace` natively.
 *
 * Feeds stall with no `error` and `load` fires only for the first frame, so there is no liveness
 * signal to watch — the connection is replaced on a timer instead. Two slots keep the swap
 * seamless: the standby is already decoding before it is shown. No PiP; that is `<video>` only.
 */
export function CameraImageFeed({ url, style, fill }: CameraImageFeedProps) {
    const { t } = useTranslation();
    const [feedState, setFeedState] = useState<FeedState>("loading");
    const [active, setActive] = useState<0 | 1>(0);
    const [slots, setSlots] = useState<Slots>(() => [withCacheBuster(url), null]);
    // Active-slot failures only — a refused standby doesn't mean the feed is unhealthy.
    const activeFailuresRef = useRef(0);
    // eslint-disable-next-line @eslint-react/naming-convention/ref-name -- plural "Refs" already conveys ref-ness
    const slotRefs = useRef<(HTMLImageElement | null)[]>([null, null]);
    const [stale, setStale] = useState(false);
    const staleTimerRef = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);

    const blockedByMixedContent = isBlockedMixedContent(url);
    const mutedColor = fill ? "whiteAlpha.800" : "fg.muted";
    const overlay = fill ? { position: "absolute" as const, inset: 0 } : {};
    const standby = (1 - active) as 0 | 1;
    const showLoading = useSpinDelay(feedState === "loading", {
        delay: 500,
        minDuration: 200,
        ssr: false,
    });

    useEffect(() => {
        const images = slotRefs.current;
        // Nothing completes an MJPEG response, so an uncleared src holds a per-host connection slot.
        return () => {
            for (const img of images) {
                if (!img) continue;
                img.src = "";
                img.removeAttribute("src");
            }
        };
    }, []);

    // An unmounted slot's connection stays open until src is cleared — unmount alone won't abort it.
    const releaseSlot = useCallback((slot: 0 | 1) => {
        const img = slotRefs.current[slot];
        if (!img) return;
        img.src = "";
        img.removeAttribute("src");
        slotRefs.current[slot] = null;
    }, []);

    // Release both slots so a late frame can't flip feedState back to "playing".
    const goOffline = useCallback(() => {
        releaseSlot(0);
        releaseSlot(1);
        setSlots([null, null]);
        setFeedState("offline");
        clearTimeout(staleTimerRef.current);
        setStale(false);
    }, [releaseSlot]);

    // Resets the staleness clock on a real handover (first frame, or a standby taking over).
    const markFresh = useCallback(() => {
        setStale(false);
        clearTimeout(staleTimerRef.current);
        staleTimerRef.current = setTimeout(
            () => setStale(true),
            RECONNECT_INTERVAL_MS + STALE_GRACE_MS,
        );
    }, []);

    useEffect(() => () => clearTimeout(staleTimerRef.current), [url]);

    useEffect(() => {
        if (blockedByMixedContent || feedState !== "loading") return;
        const id = setTimeout(goOffline, CAMERA_LOAD_TIMEOUT_MS);
        return () => clearTimeout(id);
    }, [url, blockedByMixedContent, feedState, goOffline]);

    // Re-runs on every swap, so the clock restarts from the last successful handover.
    useEffect(() => {
        if (blockedByMixedContent || feedState === "offline") return;
        if (slots[standby] !== null) return;
        const id = setTimeout(() => {
            setSlots((current) => {
                const next = [...current] as Slots;
                next[standby] = withCacheBuster(url);
                return next;
            });
        }, RECONNECT_INTERVAL_MS);
        return () => clearTimeout(id);
    }, [url, blockedByMixedContent, feedState, standby, slots]);

    const handleLoad = useCallback(
        (slot: 0 | 1) => {
            activeFailuresRef.current = 0;
            const isFirstFrame = feedState === "loading";
            setFeedState("playing");
            if (slot === active) {
                // Only the first frame is a checkpoint — a repeat load on the same slot isn't.
                if (isFirstFrame) markFresh();
                return;
            }
            markFresh();
            // Standby has a frame — show it, then drop the old connection.
            const outgoing = (1 - slot) as 0 | 1;
            releaseSlot(outgoing);
            setActive(slot);
            setSlots((current) => {
                const next = [...current] as Slots;
                next[outgoing] = null;
                return next;
            });
        },
        [active, feedState, releaseSlot, markFresh],
    );

    const handleError = useCallback(
        (slot: 0 | 1, img: HTMLImageElement) => {
            // Clearing a src fires `error` — our own teardown, not the camera failing.
            if (!img.getAttribute("src")) return;

            if (slot !== active) {
                releaseSlot(slot);
                setSlots((current) => {
                    const next = [...current] as Slots;
                    next[slot] = null;
                    return next;
                });
                // Once the active feed has failed, a refused recovery counts too — an <img>
                // only errors once per src, so this is the only remaining "still down" signal.
                if (activeFailuresRef.current === 0) return;
                activeFailuresRef.current += 1;
                if (activeFailuresRef.current >= MAX_CONSECUTIVE_FAILURES) goOffline();
                return;
            }

            activeFailuresRef.current += 1;
            if (activeFailuresRef.current >= MAX_CONSECUTIVE_FAILURES) {
                goOffline();
                return;
            }
            // Visible feed died — reconnect now rather than waiting out the timer,
            // unless a reconnect is already underway.
            setSlots((current) => {
                if (current[1 - slot] !== null) return current;
                const next = [...current] as Slots;
                next[1 - slot] = withCacheBuster(url);
                return next;
            });
        },
        [active, url, releaseSlot, goOffline],
    );

    if (blockedByMixedContent) {
        return (
            <Center py={4} h={fill ? "full" : undefined}>
                <Text fontSize="sm" color={mutedColor} textAlign="center">
                    {t("events.cameraMixedContent")}
                </Text>
            </Center>
        );
    }

    return (
        <Box
            position="relative"
            w={fill ? "full" : undefined}
            h={fill ? "full" : undefined}
            style={style}
        >
            <VisuallyHidden aria-live="polite">
                {feedState === "offline"
                    ? t("events.streamCheckUrl")
                    : stale
                      ? t("events.streamMayBeStale")
                      : ""}
            </VisuallyHidden>
            {([0, 1] as const).map((slot) => {
                const src = slots[slot];
                if (src === null) return null;
                const isVisible = slot === active && feedState === "playing";
                return (
                    <Image
                        key={slot}
                        ref={(el) => {
                            // React detaches refs before passive cleanup; keep the last non-null.
                            if (el) slotRefs.current[slot] = el;
                        }}
                        // Which slot is on screen; the standby decodes hidden until it has a frame.
                        data-slot-state={isVisible ? "active" : "standby"}
                        src={src}
                        alt={t("events.cameraFeed")}
                        onLoad={() => handleLoad(slot)}
                        onError={(e) => handleError(slot, e.currentTarget)}
                        // Not the "cover" default: a feed must never be cropped to fit.
                        fit="contain"
                        display="block"
                        maxW="100%"
                        w="full"
                        h={fill ? "full" : "auto"}
                        visibility={isVisible ? "visible" : "hidden"}
                        position={isVisible && !fill ? "static" : "absolute"}
                        top="0"
                        left="0"
                    />
                );
            })}

            {showLoading && (
                <Center py={fill ? 0 : 4} flexDir="column" gap={2} {...overlay}>
                    <Spinner size="sm" color={mutedColor} />
                    <Text fontSize="xs" color={mutedColor}>
                        {t("events.fetchingStream")}
                    </Text>
                </Center>
            )}

            {feedState === "offline" && (
                <Center py={fill ? 0 : 4} {...overlay}>
                    <Text fontSize="sm" color={mutedColor} textAlign="center">
                        {t("events.streamCheckUrl")}
                    </Text>
                </Center>
            )}

            {stale && feedState === "playing" && (
                <Center
                    position="absolute"
                    bottom="0"
                    left="0"
                    right="0"
                    py={1}
                    bg="blackAlpha.600"
                >
                    <Text fontSize="xs" color="white" textAlign="center">
                        {t("events.streamMayBeStale")}
                    </Text>
                </Center>
            )}
        </Box>
    );
}
