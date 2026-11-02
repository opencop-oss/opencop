// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { createElement, type ComponentType, type ReactElement, type ReactNode } from "react";
import { ErrorBoundary } from "react-error-boundary";
import { describeOrigin, type ContributionOrigin } from "./origin.ts";

export interface ContributionBoundaryProps {
    origin: ContributionOrigin;
    children: ReactNode;
    /** Rendered in place of the contribution after it throws. Defaults to nothing. */
    fallback?: ReactNode;
    /**
     * Called when the fallback replaces the contribution, so a host wrapper can
     * convey the degradation by something other than the fallback's own visuals
     * (an icon swap says nothing to a screen reader).
     */
    onFallback?: () => void;
    /**
     * What the contribution is currently rendering for, when that can change
     * under one mounted boundary (the element a details-panel section shows). A
     * new value clears a previous failure, so one bad element does not blank the
     * contribution for the next.
     */
    resetKey?: unknown;
}

/** The boundary that reports a caught render failure through `reportContributionFailure`. */
export function createContributionBoundary(
    reportContributionFailure: (origin: ContributionOrigin, error: unknown) => void,
): (props: ContributionBoundaryProps) => ReactElement {
    /**
     * Contain a **rendered** contribution — a tool's editor, a tool's icon.
     *
     * Without one, a throwing plugin component unwinds to the nearest host boundary,
     * which for anything mounted on the map is the whole `MapView`: one bad editor
     * replaces the entire map with an error fallback. Per-contribution containment
     * means the toolbar keeps working and only that editor is missing.
     *
     * Deliberately silent to the operator (renders `fallback`, default nothing)
     * rather than showing an error card in the map's floating chrome: the failure is
     * a defect for the log and `/admin/plugins`, and a modal-looking error panel over
     * the map is worse than a missing editor. `resetKeys` on the origin means a
     * different tool's editor mounts cleanly after this one broke; `resetKey` does the
     * same for a new subject under the same contribution.
     */
    function ContributionBoundary({
        origin,
        children,
        fallback = null,
        onFallback,
        resetKey,
    }: ContributionBoundaryProps): ReactElement {
        const key = describeOrigin(origin);
        return (
            <ErrorBoundary
                resetKeys={[key, resetKey]}
                fallbackRender={() => <>{fallback}</>}
                // Reported, not logged inline: a rendered contribution is contained by
                // the same bookkeeping as a called one, so it is attributed the same
                // way, suppressed to one line per origin, and feeds the same streak.
                //
                // `onFallback` fires from here — `componentDidCatch` — and not from
                // `fallbackRender`, which runs during render and re-runs on every
                // subsequent render: a `setState` there is a React violation and loops.
                onError={(error) => {
                    reportContributionFailure(origin, error);
                    onFallback?.();
                }}
            >
                {children}
            </ErrorBoundary>
        );
    }
    return ContributionBoundary;
}

/**
 * Wrap a contributed component once in `Boundary`, attributed to `origin`. When
 * its props carry an `element`, that element resets the boundary: a panel stays
 * mounted while the operator selects another element, and a failure belongs to
 * the element that caused it.
 */
export function containComponentIn<P extends object>(
    Boundary: (props: ContributionBoundaryProps) => ReactElement,
    origin: ContributionOrigin,
    Component: ComponentType<P>,
): ComponentType<P> {
    const Contained = (props: P) =>
        createElement(Boundary, {
            origin,
            resetKey: (props as { element?: unknown }).element,
            children: createElement(Component, props),
        });
    Contained.displayName = `Contained(${Component.displayName ?? Component.name ?? "Contribution"})`;
    return Contained;
}
