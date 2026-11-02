// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/* eslint-disable i18next/no-literal-string */
import { Box, VStack } from "@chakra-ui/react";
import { useMapManager } from "./MapManagerContext";
import { useCallback, useEffect, useMemo, useState } from "react";
import { RingBuffer, Stat } from "@opencop/ui";
import type { DeckMetrics } from "./MapManager";
import { useMetric } from "./use-metric.hook";

export interface DeckStatsProps {
    placement: "top" | "bottom";
}

export type RenderMetrics = {
    fps: boolean;
    cpu: boolean;
    cpuPf: boolean;
    attrPct: boolean;
    gpu: boolean;
    gpuPf: boolean;
};

const bufferSize = 120;

/** Persistent ring buffers keyed by stat label. Survives re-renders. */
function createBuffers() {
    return {
        fps: new RingBuffer(bufferSize),
        cpu: new RingBuffer(bufferSize),
        cpuPf: new RingBuffer(bufferSize),
        attrPct: new RingBuffer(bufferSize),
        gpu: new RingBuffer(bufferSize),
        gpuPf: new RingBuffer(bufferSize),
    };
}

function useBuffers() {
    const [buffers] = useState(createBuffers);
    return buffers;
}

const targetFps = 60;
const targetCpuPercentage = 0.4;
const targetAttrPercentage = 0.4;
const targetGpuPercentage = 1 - targetCpuPercentage;

const targetFrameTime = (1 / targetFps) * 1000; // ms
const targetCpuTimePF = targetFrameTime * targetCpuPercentage;
const targetCpuTime = targetCpuTimePF * 60;
const targetGpuTimePF = targetFrameTime * targetGpuPercentage;
const targetGpuTime = targetGpuTimePF * 60;

export function RenderMetrics({ placement }: DeckStatsProps) {
    const renderMetrics: RenderMetrics = useMetric("render") as RenderMetrics;
    const mapManager = useMapManager();
    const [stats, setStats] = useState<DeckMetrics | null>(null);
    const buffers = useBuffers();
    const enabledMetrics = useMemo(
        () =>
            new Set(
                Object.entries(renderMetrics)
                    .filter(([, enabled]) => enabled)
                    .map(([metric]) => metric),
            ),
        [renderMetrics],
    );

    const onMetrics = useCallback(
        (metrics: DeckMetrics) => {
            const totalCpu = metrics.cpuTime;
            const attrPct = totalCpu > 0 ? (metrics.updateAttributesTime / totalCpu) * 100 : 0;

            buffers.fps.push(metrics.fps);
            buffers.cpu.push(metrics.cpuTime);
            buffers.cpuPf.push(metrics.cpuTimePerFrame);
            buffers.attrPct.push(attrPct);
            buffers.gpu.push(metrics.gpuTime);
            buffers.gpuPf.push(metrics.gpuTimePerFrame);

            setStats({ ...metrics });
        },
        [buffers],
    );

    useEffect(() => {
        if (enabledMetrics.size === 0) return;
        return mapManager.onMetrics(onMetrics);
    }, [mapManager, enabledMetrics, onMetrics]);

    if (!renderMetrics || !stats) return null;

    const totalCpu = stats.cpuTime;

    return (
        <Box
            w="60"
            pos="absolute"
            left="1"
            {...(placement === "top" ? { top: "9" } : { bottom: "9" })}
        >
            <VStack gap="1" align="stretch">
                {enabledMetrics.has("fps") && (
                    <Stat
                        label="FPS"
                        value={stats.fps}
                        unit=""
                        desc="Frames per second"
                        buffer={buffers.fps}
                        higherIsBetter
                        good={targetFps}
                        warn={targetFps * 0.8}
                    />
                )}
                {enabledMetrics.has("cpu") && (
                    <Stat
                        label="CPU"
                        value={stats.cpuTime}
                        unit="ms"
                        desc="Time spent on CPU processing over last 60 frames."
                        buffer={buffers.cpu}
                        good={targetCpuTime}
                        warn={targetCpuTime * 1.2}
                    />
                )}
                {enabledMetrics.has("cpuPf") && (
                    <Stat
                        label="CPU/F"
                        value={stats.cpuTimePerFrame}
                        unit="ms"
                        desc="CPU time per frame."
                        buffer={buffers.cpuPf}
                        good={targetCpuTimePF}
                        warn={targetCpuTimePF * 1.2}
                    />
                )}
                {enabledMetrics.has("attrPct") && (
                    <Stat
                        label="Attr %"
                        value={totalCpu > 0 ? (stats.updateAttributesTime / totalCpu) * 100 : 0}
                        unit="%"
                        desc="Percentage of CPU time spent updating attributes."
                        buffer={buffers.attrPct}
                        good={targetAttrPercentage * 100}
                        warn={targetAttrPercentage * 100 * 1.2}
                    />
                )}
                {enabledMetrics.has("gpu") && (
                    <Stat
                        label="GPU"
                        value={stats.gpuTime}
                        unit="ms"
                        desc="Time spent on GPU processing over last 60 frames."
                        buffer={buffers.gpu}
                        good={targetGpuTime}
                        warn={targetGpuTime * 1.2}
                    />
                )}
                {enabledMetrics.has("gpuPf") && (
                    <Stat
                        label="GPU/F"
                        value={stats.gpuTimePerFrame}
                        unit="ms"
                        desc="GPU time per frame."
                        buffer={buffers.gpuPf}
                        good={targetGpuTimePF}
                        warn={targetGpuTimePF * 1.2}
                    />
                )}
            </VStack>
        </Box>
    );
}
