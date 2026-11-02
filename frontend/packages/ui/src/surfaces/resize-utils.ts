// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * Converts a drag pixel delta into updated flex values for two adjacent resizable sections.
 * Returns null if the container has no height yet (pre-mount).
 */
export function computeFlexSizes(
    prev: Record<string, number>,
    delta: number,
    labelA: string,
    labelB: string,
    totalH: number,
    minSectionH = 90,
): Record<string, number> | null {
    const flexA = prev[labelA] ?? 1;
    const flexB = prev[labelB] ?? 1;
    const totalFlex = flexA + flexB;
    if (totalH === 0 || totalFlex === 0) return null;

    const pixelsPerFlex = totalH / totalFlex;
    const minFlex = minSectionH / pixelsPerFlex;
    const deltaFlex = delta / pixelsPerFlex;

    const newFlexA = Math.max(minFlex, Math.min(totalFlex - minFlex, flexA + deltaFlex));
    const newFlexB = totalFlex - newFlexA;

    const result: Record<string, number> = { ...prev };
    result[labelA] = Math.round(newFlexA * 10000) / 10000;
    result[labelB] = Math.round(newFlexB * 10000) / 10000;
    return result;
}
