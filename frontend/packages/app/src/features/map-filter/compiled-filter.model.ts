// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { Element } from "@opencop/elements";
import type { Predicate } from "@opencop/ui";
import type { MapFilter } from "./map-filter.store";
import assertNever from "assert-never";

/**
 * "Compiles" filter expressions into executable instructions, specializing all static configurations (compiles away branches).
 */

export type CompiledExpressionContext = {
    element: Element;
};

export interface CompiledExpression {
    execute(ctx: CompiledExpressionContext): boolean;
}

export class AllExpression implements CompiledExpression {
    public readonly name: string;
    public readonly predicates: readonly CompiledExpression[];

    constructor(name: string, predicates: CompiledExpression[]) {
        this.name = name;
        this.predicates = predicates;
    }

    execute(ctx: CompiledExpressionContext): boolean {
        return this.predicates.every((predicate) => predicate.execute(ctx));
    }
}

export class TruthyExpression implements CompiledExpression {
    public readonly name: string;
    public readonly predicates: readonly CompiledExpression[] = [];

    constructor(name: string) {
        this.name = name;
    }

    execute(): boolean {
        return true;
    }
}

export class AnyExpression implements CompiledExpression {
    public readonly name: string;
    public readonly predicates: readonly CompiledExpression[];

    constructor(name: string, predicates: CompiledExpression[]) {
        this.name = name;
        this.predicates = predicates;
    }

    execute(ctx: CompiledExpressionContext): boolean {
        return this.predicates.some((predicate) => predicate.execute(ctx));
    }
}

export class EqualsExpression implements CompiledExpression {
    public readonly value: string;
    private readonly transform: (res: boolean) => boolean;
    private readonly accessor: (element: Element) => string | undefined;

    constructor(
        value: string,
        transform: (res: boolean) => boolean,
        accessor: (element: Element) => string | undefined,
    ) {
        this.value = value;
        this.accessor = accessor;
        this.transform = transform;
    }

    execute({ element }: CompiledExpressionContext): boolean {
        const value = this.accessor(element);
        return this.transform(value === this.value);
    }
}

export class ExistsExpression implements CompiledExpression {
    public readonly transform: (res: boolean) => boolean;
    private readonly accessor: (element: Element) => unknown;

    constructor(transform: (res: boolean) => boolean, accessor: (element: Element) => unknown) {
        this.transform = transform;
        this.accessor = accessor;
    }

    execute({ element }: CompiledExpressionContext): boolean {
        const res = this.accessor(element) !== undefined;
        return this.transform(res);
    }
}

export class InExpression implements CompiledExpression {
    public readonly transform: (res: boolean) => boolean;
    public readonly values: readonly string[];
    private readonly accessor: (element: Element) => string | undefined;

    constructor(
        values: string[],
        transform: (res: boolean) => boolean,
        accessor: (element: Element) => string | undefined,
    ) {
        this.values = values;
        this.accessor = accessor;
        this.transform = transform;
    }

    execute({ element }: CompiledExpressionContext): boolean {
        const v = this.accessor(element);
        for (const value of this.values) {
            if (v === value) return this.transform(true);
        }
        return this.transform(false);
    }
}

export class SubstringInExpression implements CompiledExpression {
    public readonly transform: (res: boolean) => boolean;
    public readonly values: readonly string[];
    public readonly start: number;
    public readonly end: number;
    private readonly accessor: (element: Element) => string | undefined;

    constructor(
        values: string[],
        start: number,
        end: number,
        transform: (res: boolean) => boolean,
        accessor: (element: Element) => string | undefined,
    ) {
        this.values = values;
        this.start = start;
        this.end = end;
        this.accessor = accessor;
        this.transform = transform;
    }

    execute({ element }: CompiledExpressionContext): boolean {
        const value = this.accessor(element);
        if (value === undefined) return this.transform(false); // Intended, matches backend behavior.
        const v = value.slice(this.start, this.end);

        for (const value of this.values) {
            if (v === value) return this.transform(true);
        }
        return this.transform(false);
    }
}

export type CompiledFilter = AnyExpression | AllExpression | TruthyExpression;

/** The element value each property path a map filter can evaluate reads. */
const ACCESSORS: Record<string, (element: Element) => string | undefined> = {
    "properties.opencop.displayName": (element) => element.getDisplayName(),
    "properties.opencop.element.symbol": (element) => element.getCode(),
};

/** Whether a map filter can evaluate a predicate on `path`. */
export function canCompilePath(path: string): boolean {
    return Object.hasOwn(ACCESSORS, path);
}

function accessorFromPredicate(predicate: Predicate) {
    const accessor = canCompilePath(predicate.path) ? ACCESSORS[predicate.path] : undefined;
    if (accessor === undefined) throw new Error("Invalid path");

    if (predicate.caseInsensitive) return (element: Element) => accessor(element)?.toLowerCase();
    return accessor;
}

export function compile(filter: MapFilter): CompiledFilter {
    const predicates: CompiledExpression[] = [];

    filter.predicates.forEach((predicate) => {
        const transform = predicate.negate ? (res: boolean) => !res : (res: boolean) => res;
        switch (predicate.operation) {
            case "EQUALS":
                predicates.push(
                    new EqualsExpression(
                        predicate.caseInsensitive
                            ? predicate.values[0].toLowerCase()
                            : predicate.values[0],
                        transform,
                        accessorFromPredicate(predicate),
                    ),
                );
                break;
            case "EXISTS":
                predicates.push(new ExistsExpression(transform, accessorFromPredicate(predicate)));
                break;
            case "IN":
                predicates.push(
                    new InExpression(
                        predicate.values.map((value) =>
                            predicate.caseInsensitive ? value.toLowerCase() : value,
                        ),
                        transform,
                        accessorFromPredicate(predicate),
                    ),
                );
                break;
            case "SUBSTRING_IN":
                predicates.push(
                    new SubstringInExpression(
                        predicate.values.map((value) =>
                            predicate.caseInsensitive ? value.toLowerCase() : value,
                        ),
                        predicate.offset,
                        predicate.offset + predicate.length,
                        transform,
                        accessorFromPredicate(predicate),
                    ),
                );
                break;
            default:
                assertNever(predicate);
        }
    });

    if (filter.combine === "ALL") return new AllExpression(filter.name, predicates);
    else
        return predicates.length > 0
            ? new AnyExpression(filter.name, predicates)
            : new TruthyExpression(filter.name); // .some returns false on empty array, force true
}
