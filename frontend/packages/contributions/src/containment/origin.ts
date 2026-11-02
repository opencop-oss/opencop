// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * Where a contributed callback or component came from, so a contained failure can
 * be attributed to the thing that caused it.
 *
 * A subject — plugin or native — contributes executable code to the host, and the
 * host calls it from inside its own render and routing paths. When it throws, the
 * stack names a host component and the log names a host concern, which is exactly
 * the wrong place to start looking. Carrying provenance to the call site is what
 * turns "the map is blank" into "symbology-plugin's graphic renderer threw in
 * `mapLayer`".
 */
export interface ContributionOrigin {
    /** Who contributed it. Absent only where the registry keeps no owner yet (a native tool). */
    readonly owner?: ContributionOwner;
    /** Which contribution: a renderer identity, a tool descriptor id. */
    readonly contribution: string;
    /** Which call is running — `match`, `preview`, `mapLayer`, `editor`, … */
    readonly phase: string;
}

/** Whether an owner was installed by the composition root (`native`) or the plugin manager (`plugin`). */
export type OwnerKind = "native" | "plugin";

/**
 * Who contributed an entry. `ownerId` is the owner's namespaced id
 * (`opencop.shapes`, `acme.heatmap`); `ownerKind` is recorded by the host from the
 * context that registered the entry, never claimed by the owner and never
 * inferred from registration order.
 */
export interface ContributionOwner {
    readonly ownerId: string;
    readonly ownerKind: OwnerKind;
}

export function nativeOwner(ownerId: string): ContributionOwner {
    return { ownerId, ownerKind: "native" };
}

export function pluginOwner(ownerId: string): ContributionOwner {
    return { ownerId, ownerKind: "plugin" };
}

/**
 * The origin as a log and streak key. A plugin reads `<pluginId>:…`; a native
 * reads `native:<subject>:…`, so a plugin whose id equals a subject's never
 * shares a key with it.
 */
export function describeOrigin({ owner, contribution, phase }: ContributionOrigin): string {
    const who =
        owner?.ownerKind === "plugin"
            ? owner.ownerId
            : owner
              ? `native:${owner.ownerId}`
              : "native";
    return `${who}:${contribution}:${phase}`;
}
