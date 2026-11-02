// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { createContext, use, useState, useCallback, useRef, useEffect, useMemo } from "react";
import type { TripWaypoint, GapThresholds } from "./history.util";
import { DEFAULT_GAP_THRESHOLDS } from "./history.util";
import Logger from "js-logger";

const logger = Logger.get("events");

// Gap snap tolerance for seekTo - snaps to gap waypoints within this threshold
const GAP_SNAP_TOLERANCE_MS = 1_000;

export interface SavedViewState {
    center: { lng: number; lat: number };
    zoom: number;
    bearing: number;
    pitch: number;
}

interface TripState {
    active: boolean;
    displayName: string;
    waypoints: TripWaypoint[];
    startTime: number; // ms, first waypoint timestamp
    endTime: number; // ms, last waypoint timestamp
    currentTime: number; // ms, within [startTime, endTime]
    playing: boolean;
    speed: 2 | 4 | 10;
    showAnimation: boolean; // Toggle for animated overlay
    hasZoomed: boolean; // Whether we've zoomed to the trip
    savedView: SavedViewState | null; // Previous map view to restore
    hideGaps: boolean; // Whether to hide gap segments
    gapThresholds: GapThresholds; // Thresholds for gap detection
}

interface TripContextValue extends TripState {
    activateTrip: (waypoints: TripWaypoint[], displayName: string) => void;
    deactivateTrip: () => void;
    setPlaying: (playing: boolean) => void;
    setSpeed: (speed: 2 | 4 | 10) => void;
    increaseSpeed: () => void;
    decreaseSpeed: () => void;
    setShowAnimation: (show: boolean) => void;
    seekTo: (timestamp: number) => void;
    goToNextWaypoint: () => void;
    goToPreviousWaypoint: () => void;
    markAsZoomed: (savedView: SavedViewState) => void;
    returnToSavedView: () => void;
    setHideGaps: (hide: boolean) => void;
    setGapThresholds: (thresholds: GapThresholds) => void;
}

const TripContext = createContext<TripContextValue | null>(null);

const INITIAL_STATE: TripState = {
    active: false,
    displayName: "",
    waypoints: [],
    startTime: 0,
    endTime: 0,
    currentTime: 0,
    playing: false,
    speed: 2,
    showAnimation: false,
    hasZoomed: false,
    savedView: null,
    hideGaps: false, // Default to showing gaps
    gapThresholds: DEFAULT_GAP_THRESHOLDS,
};

export function TripProvider({ children }: { children: React.ReactNode }) {
    const [state, setState] = useState<TripState>(INITIAL_STATE);
    const animationFrameRef = useRef<number | null>(null);
    const lastTickRef = useRef<number>(0);

    const tick = useCallback((deltaMs: number) => {
        setState((prev) => {
            if (!prev.active || !prev.playing) return prev;

            // Simple animation: advance by real deltaMs * speed multiplier
            // This makes 1 second of real time = prev.speed seconds of trip time
            const increment = deltaMs * prev.speed;
            let nextTime = prev.currentTime + increment;

            // Wrap around when we reach the end
            if (nextTime >= prev.endTime) {
                nextTime = prev.startTime;
            }

            return { ...prev, currentTime: nextTime };
        });
    }, []);

    // Animation loop
    useEffect(() => {
        if (!state.active || !state.playing || !state.showAnimation) {
            if (animationFrameRef.current) {
                cancelAnimationFrame(animationFrameRef.current);
                animationFrameRef.current = null;
            }
            return;
        }

        const animate = (now: number) => {
            const deltaMs = now - lastTickRef.current;
            lastTickRef.current = now;
            tick(deltaMs);
            animationFrameRef.current = requestAnimationFrame(animate);
        };

        lastTickRef.current = performance.now();
        animationFrameRef.current = requestAnimationFrame(animate);

        return () => {
            if (animationFrameRef.current) {
                cancelAnimationFrame(animationFrameRef.current);
                animationFrameRef.current = null;
            }
        };
    }, [state.active, state.playing, state.showAnimation, tick]);

    const activateTrip = useCallback((waypoints: TripWaypoint[], displayName: string) => {
        if (waypoints.length < 2) {
            logger.warn("[TripContext] Cannot activate trip with fewer than 2 waypoints");
            return;
        }

        const startTime = waypoints[0].timestamp;
        const endTime = waypoints[waypoints.length - 1].timestamp;

        setState((prev) => ({
            active: true,
            displayName,
            waypoints,
            startTime,
            endTime,
            currentTime: startTime,
            playing: true,
            speed: 2,
            showAnimation: false, // Start with static path only
            hasZoomed: prev.hasZoomed, // Preserve zoom state
            savedView: prev.savedView, // Preserve saved view
            hideGaps: prev.hideGaps, // Preserve gap settings
            gapThresholds: prev.gapThresholds,
        }));
    }, []);

    const deactivateTrip = useCallback(() => {
        setState((prev) => ({
            ...INITIAL_STATE,
            // Preserve gap settings when deactivating
            hideGaps: prev.hideGaps,
            gapThresholds: prev.gapThresholds,
        }));
    }, []);

    const setPlaying = useCallback((playing: boolean) => {
        setState((prev) => ({ ...prev, playing }));
    }, []);

    const setSpeed = useCallback((speed: 2 | 4 | 10) => {
        setState((prev) => ({ ...prev, speed }));
    }, []);

    const increaseSpeed = useCallback(() => {
        setState((prev) => {
            const speeds: Array<2 | 4 | 10> = [2, 4, 10];
            const currentIndex = speeds.indexOf(prev.speed);
            const nextIndex = Math.min(currentIndex + 1, speeds.length - 1);
            return { ...prev, speed: speeds[nextIndex] };
        });
    }, []);

    const decreaseSpeed = useCallback(() => {
        setState((prev) => {
            const speeds: Array<2 | 4 | 10> = [2, 4, 10];
            const currentIndex = speeds.indexOf(prev.speed);
            const prevIndex = Math.max(currentIndex - 1, 0);
            return { ...prev, speed: speeds[prevIndex] };
        });
    }, []);

    const setShowAnimation = useCallback((show: boolean) => {
        setState((prev) => {
            // Reset to start and ensure playing when enabling animation
            if (show && !prev.showAnimation) {
                return { ...prev, showAnimation: show, currentTime: prev.startTime, playing: true };
            }
            return { ...prev, showAnimation: show };
        });
    }, []);

    const seekTo = useCallback((timestamp: number) => {
        setState((prev) => {
            // Clamp to valid range
            let clampedTime = Math.max(prev.startTime, Math.min(prev.endTime, timestamp));

            // If seeking into a gap, skip to the end of the gap (the next non-gap waypoint)
            if (!prev.hideGaps) {
                // Find if we're landing on or just after a gap waypoint
                const gapWaypoint = prev.waypoints.find(
                    (wp) =>
                        wp.hasGapBefore &&
                        Math.abs(wp.timestamp - clampedTime) < GAP_SNAP_TOLERANCE_MS,
                );

                if (gapWaypoint) {
                    // Jump to this gap waypoint
                    clampedTime = gapWaypoint.timestamp;
                }
            }

            return { ...prev, currentTime: clampedTime };
        });
    }, []);

    const goToNextWaypoint = useCallback(() => {
        setState((prev) => {
            if (prev.waypoints.length === 0) return prev;

            // Find the next waypoint after current time
            const nextWaypoint = prev.waypoints.find((wp) => wp.timestamp > prev.currentTime);
            if (nextWaypoint) {
                return { ...prev, currentTime: nextWaypoint.timestamp };
            }

            // If no next waypoint, wrap to start
            return { ...prev, currentTime: prev.startTime };
        });
    }, []);

    const goToPreviousWaypoint = useCallback(() => {
        setState((prev) => {
            if (prev.waypoints.length === 0) return prev;

            // Find the previous waypoint before current time
            const previousWaypoints = prev.waypoints.filter(
                (wp) => wp.timestamp < prev.currentTime,
            );
            if (previousWaypoints.length > 0) {
                const previousWaypoint = previousWaypoints[previousWaypoints.length - 1];
                return { ...prev, currentTime: previousWaypoint.timestamp };
            }

            // If no previous waypoint, go to end
            return { ...prev, currentTime: prev.endTime };
        });
    }, []);

    const markAsZoomed = useCallback((savedView: SavedViewState) => {
        setState((prev) => ({ ...prev, hasZoomed: true, savedView }));
    }, []);

    const returnToSavedView = useCallback(() => {
        setState((prev) => ({ ...prev, hasZoomed: false }));
    }, []);

    const setHideGaps = useCallback((hide: boolean) => {
        setState((prev) => ({ ...prev, hideGaps: hide }));
    }, []);

    const setGapThresholds = useCallback((thresholds: GapThresholds) => {
        setState((prev) => ({ ...prev, gapThresholds: thresholds }));
    }, []);

    const value: TripContextValue = useMemo(
        () => ({
            ...state,
            activateTrip,
            deactivateTrip,
            setPlaying,
            setSpeed,
            increaseSpeed,
            decreaseSpeed,
            setShowAnimation,
            seekTo,
            goToNextWaypoint,
            goToPreviousWaypoint,
            markAsZoomed,
            returnToSavedView,
            setHideGaps,
            setGapThresholds,
        }),
        [
            state,
            activateTrip,
            deactivateTrip,
            setPlaying,
            setSpeed,
            increaseSpeed,
            decreaseSpeed,
            setShowAnimation,
            seekTo,
            goToNextWaypoint,
            goToPreviousWaypoint,
            markAsZoomed,
            returnToSavedView,
            setHideGaps,
            setGapThresholds,
        ],
    );

    return <TripContext value={value}>{children}</TripContext>;
}

export function useTrip(): TripContextValue {
    const context = use(TripContext);
    if (!context) {
        throw new Error("useTrip must be used within a TripProvider");
    }
    return context;
}
