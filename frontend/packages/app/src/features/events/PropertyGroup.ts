// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { formatKey } from "./helpers.util";

export type DisplayProperty = {
    key: string;
    value: unknown;
    formatted: string;
    editable?: boolean;
};

export class PropertyGroup {
    private readonly _label: string;
    private readonly _props: Record<string, boolean>;

    constructor(label: string);
    constructor(label: string, props: Record<string, boolean>);
    constructor(label: string, props?: Record<string, boolean>) {
        this._label = label;
        this._props = props ?? {};
    }

    register(prop: string, editable?: boolean): this {
        this._props[prop] = !!editable;
        return this;
    }

    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    display(obj: any, format?: (v: any) => string): DisplayProperty[] {
        return Object.keys(this._props).reduce<DisplayProperty[]>((acc, key) => {
            if (key in obj) {
                acc.push({
                    key,
                    value: format ? format(obj[key]) : obj[key],
                    formatted: formatKey(key),
                    editable: this._props[key],
                });
            }

            return acc;
        }, []);
    }

    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    displayAll(obj: object, format?: (v: any) => string): DisplayProperty[] {
        return Object.entries(obj).map(([key, val]) => ({
            key,
            value: format ? format(val) : val,
            formatted: formatKey(key),
            editable: this._props[key] ?? true,
        }));
    }

    get label() {
        return this._label;
    }

    get props() {
        return this._props;
    }
}
