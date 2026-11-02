// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ComponentType } from "react";
import type { Containment } from "./containment.ts";
import type { ContributionOrigin } from "./origin.ts";

type Callable = (...args: never[]) => unknown;
type Constructible = abstract new (...args: never[]) => unknown;

/** Whether a member's value is code the host calls or renders: a function, or a component class. */
type IsCode<V> = V extends Callable ? true : V extends Constructible ? true : false;

/** The members of a contract that hold contributed code — every function- or component-valued key. */
export type CodeMembers<T> = {
    [K in keyof T]-?: true extends IsCode<NonNullable<T[K]>> ? K : never;
}[keyof T];

/**
 * What one member costs when it throws. `render` wraps a component in the boundary
 * (it renders nothing). `fallback` is what a call returns instead — host code, not
 * guarded; `invoke`, when given, is how the host calls the member, inside the guard
 * (to read a lazy iterable into an array before the guard is left, say).
 */
export type MemberPolicy<V> =
    | (V extends ComponentType<never> ? { readonly render: true } : never)
    | (V extends (...args: infer A) => infer R
          ? {
                readonly fallback: (error: unknown, ...args: A) => R;
                readonly invoke?: (member: V, ...args: A) => R;
            }
          : never);

/** A policy as `containMembers` applies it, once its member's own types are no longer needed. */
type AppliedPolicy =
    | { readonly render: true }
    | {
          readonly fallback: (error: unknown, ...args: unknown[]) => unknown;
          readonly invoke?: (
              member: (...args: unknown[]) => unknown,
              ...args: unknown[]
          ) => unknown;
      };

/**
 * A contribution type's containment: a policy for **every** member that holds code.
 * Keyed by {@link CodeMembers}, so a function or component added to the contract
 * without a policy here does not compile — containment cannot be forgotten.
 */
export type ContainmentPolicy<T> = {
    readonly [K in CodeMembers<T>]-?: MemberPolicy<NonNullable<T[K]>>;
};

/**
 * `item` with every member `policy` names wrapped once: a component in the boundary,
 * a function in the guard, each attributed to `origin(member)` with its key built here
 * rather than on every call. Members the item does not have stay absent.
 */
export function containMembers<T extends object>(
    containment: Containment,
    item: T,
    origin: (member: string) => ContributionOrigin,
    policy: ContainmentPolicy<T>,
): T {
    const contained: Record<string, unknown> = { ...(item as Record<string, unknown>) };
    const members = item as Record<string, unknown>;
    for (const [member, rule] of Object.entries(policy) as [string, AppliedPolicy][]) {
        const value = members[member];
        if (value === undefined) continue;
        if ("render" in rule) {
            contained[member] = containment.containComponent(
                origin(member),
                value as ComponentType,
            );
            continue;
        }
        const bound = (value as (...args: unknown[]) => unknown).bind(item);
        const { invoke } = rule;
        const call = invoke ? (...args: unknown[]) => invoke(bound, ...args) : bound;
        contained[member] = containment.guard(origin(member), call, rule.fallback);
    }
    return contained as T;
}
