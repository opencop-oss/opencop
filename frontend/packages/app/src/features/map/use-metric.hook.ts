// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useSyncExternalStore } from "react";

class Metrics {
    static readonly metrics: Record<string, Record<string, boolean>> = {};
    static readonly snapshots: Record<string, Record<string, boolean>> = {};
    static readonly subs: Record<string, Set<() => void>> = {};

    private constructor() {}

    static registerScope(scope: string, metrics: Record<string, boolean>) {
        this.metrics[scope] = metrics;
        this.snapshots[scope] = { ...metrics };
    }

    static sub(scope: string, cb: () => void) {
        (this.subs[scope] ??= new Set()).add(cb);
        return () => this.subs[scope].delete(cb);
    }

    static toggle(scope?: string, metric?: string) {
        if (!scope) {
            for (const s of Object.keys(this.metrics)) {
                for (const m of Object.keys(this.metrics[s])) {
                    this.metrics[s][m] = !this.metrics[s][m];
                }
                this.snapshots[s] = { ...this.metrics[s] };
                (this.subs[s] ??= new Set()).forEach((cb) => cb());
            }
        } else if (this.metrics[scope]) {
            if (!metric) {
                for (const m of Object.keys(this.metrics[scope])) {
                    this.metrics[scope][m] = !this.metrics[scope][m];
                }
            } else if (this.metrics[scope][metric] !== undefined) {
                this.metrics[scope][metric] = !this.metrics[scope][metric];
            }
            this.snapshots[scope] = { ...this.metrics[scope] };
            (this.subs[scope] ??= new Set()).forEach((cb) => cb());
        }
    }

    static getMetricsForScope(scope: string): Record<string, boolean> | undefined {
        return this.snapshots[scope];
    }
}

declare global {
    interface Window {
        __metrics: typeof Metrics;
    }
}

window.__metrics = Metrics;

export function useMetric(scope: string) {
    return useSyncExternalStore(
        (cb) => {
            return Metrics.sub(scope, cb);
        },
        () => Metrics.getMetricsForScope(scope),
    );
}

Metrics.registerScope("render", {
    fps: false,
    cpu: false,
    cpuPf: false,
    attrPct: false,
    gpu: false,
    gpuPf: false,
});
