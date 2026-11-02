// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { isSidcString } from "./sidc-format";
import { getMultipointSymbolSets } from "./strategy";
import { GENERATED_SIDC_AMPLIFIERS, GENERATED_SIDC_SYMBOL_SETS } from "./sidc.generated";

export interface SIDCLabel {
    label: string;
    localeKey: string;
}

export type SIDCMap = Record<string, SIDCLabel>;

export interface SIDCEntityType extends SIDCLabel {
    entitySubTypes: Record<string, SIDCLabel>;
}

export interface SIDCEntity extends SIDCLabel {
    entityTypes: Record<string, SIDCEntityType>;
}

export interface SIDCSymbolSet extends SIDCLabel {
    entities: Record<string, SIDCEntity>;
    specialEntitySubTypes?: Record<string, SIDCLabel>;
    sectorOneModifiers: Record<string, SIDCLabel>;
    sectorTwoModifiers?: Record<string, SIDCLabel>;
}

export interface SIDCAmplifier extends SIDCLabel {
    descriptors: SIDCMap;
}

export interface SIDCFields {
    version: string;
    context: string;
    identity: string;
    symbolSet: string;
    status: string;
    hqtf: string;
    amplifier: string;
    descriptor: string;
    entity: string;
    entityType: string;
    entitySubType: string;
    modifier1: string;
    modifier2: string;
}

const SIDC_DEFAULTS: SIDCFields = {
    version: "10",
    context: "0",
    identity: "3",
    symbolSet: "10",
    status: "0",
    hqtf: "0",
    amplifier: "0",
    descriptor: "0",
    entity: "00",
    entityType: "00",
    entitySubType: "00",
    modifier1: "00",
    modifier2: "00",
};

export class SIDC {
    private readonly _version: string;
    private readonly _context: string;
    private readonly _identity: string;
    private readonly _symbolSet: string;
    private readonly _status: string;
    private readonly _hqtf: string;
    private readonly _amplifier: string;
    private readonly _descriptor: string;
    private readonly _entity: string;
    private readonly _entityType: string;
    private readonly _entitySubType: string;
    private readonly _modifier1: string;
    private readonly _modifier2: string;

    constructor(fields?: Partial<SIDCFields>) {
        const f = { ...SIDC_DEFAULTS, ...fields };
        this._version = f.version;
        this._context = f.context;
        this._identity = f.identity;
        this._symbolSet = f.symbolSet;
        this._status = f.status;
        this._hqtf = f.hqtf;
        this._amplifier = f.amplifier;
        this._descriptor = f.descriptor;
        this._entity = f.entity;
        this._entityType = f.entityType;
        this._entitySubType = f.entitySubType;
        this._modifier1 = f.modifier1;
        this._modifier2 = f.modifier2;
    }

    // Getters

    get version(): string {
        return this._version;
    }
    get context(): string {
        return this._context;
    }
    get identity(): string {
        return this._identity;
    }
    get symbolSet(): string {
        return this._symbolSet;
    }
    get status(): string {
        return this._status;
    }
    get hqtf(): string {
        return this._hqtf;
    }
    get amplifier(): string {
        return this._amplifier;
    }
    get descriptor(): string {
        return this._descriptor;
    }
    get entity(): string {
        return this._entity;
    }
    get entityType(): string {
        return this._entityType;
    }
    get entitySubType(): string {
        return this._entitySubType;
    }
    get modifier1(): string {
        return this._modifier1;
    }
    get modifier2(): string {
        return this._modifier2;
    }

    // With-methods (return new immutable instance)

    private clone(overrides: Partial<SIDCFields>): SIDC {
        return new SIDC({
            version: this._version,
            context: this._context,
            identity: this._identity,
            symbolSet: this._symbolSet,
            status: this._status,
            hqtf: this._hqtf,
            amplifier: this._amplifier,
            descriptor: this._descriptor,
            entity: this._entity,
            entityType: this._entityType,
            entitySubType: this._entitySubType,
            modifier1: this._modifier1,
            modifier2: this._modifier2,
            ...overrides,
        });
    }

    get basicId(): string {
        return this._symbolSet + this._entity + this._entityType + this._entitySubType;
    }

    withVersion(value: string): SIDC {
        return this.clone({ version: value });
    }
    withContext(value: string): SIDC {
        return this.clone({ context: value });
    }
    withIdentity(value: string): SIDC {
        return this.clone({ identity: value });
    }
    withSymbolSet(value: string): SIDC {
        return this.clone({
            symbolSet: value,
            entity: "00",
            entityType: "00",
            entitySubType: "00",
            modifier1: "00",
            modifier2: "00",
        });
    }
    withStatus(value: string): SIDC {
        return this.clone({ status: value });
    }
    withHqtf(value: string): SIDC {
        return this.clone({ hqtf: value });
    }
    withAmplifier(value: string): SIDC {
        return this.clone({ amplifier: value, descriptor: "0" });
    }
    withDescriptor(value: string): SIDC {
        return this.clone({ descriptor: value });
    }
    withEntity(value: string): SIDC {
        return this.clone({ entity: value, entityType: "00", entitySubType: "00" });
    }
    withEntityType(value: string): SIDC {
        return this.clone({ entityType: value, entitySubType: "00" });
    }
    withEntitySubType(value: string): SIDC {
        return this.clone({ entitySubType: value });
    }
    withModifier1(value: string): SIDC {
        return this.clone({ modifier1: value });
    }
    withModifier2(value: string): SIDC {
        return this.clone({ modifier2: value });
    }

    // Lookup helpers

    get symbolSetData(): SIDCSymbolSet | undefined {
        return GENERATED_SIDC_SYMBOL_SETS[this._symbolSet];
    }

    // Serialization

    toArray(): string[] {
        return [
            this._version,
            this._context,
            this._identity,
            this._symbolSet,
            this._status,
            this._hqtf,
            this._amplifier,
            this._descriptor,
            this._entity,
            this._entityType,
            this._entitySubType,
            this._modifier1,
            this._modifier2,
        ];
    }

    toString(): string {
        return this.toArray().join("");
    }

    // Factory methods

    static fromString(sidc: string): SIDC {
        return new SIDC({
            version: sidc.slice(0, 2),
            context: sidc.slice(2, 3),
            identity: sidc.slice(3, 4),
            symbolSet: sidc.slice(4, 6),
            status: sidc.slice(6, 7),
            hqtf: sidc.slice(7, 8),
            amplifier: sidc.slice(8, 9),
            descriptor: sidc.slice(9, 10),
            entity: sidc.slice(10, 12),
            entityType: sidc.slice(12, 14),
            entitySubType: sidc.slice(14, 16),
            modifier1: sidc.slice(16, 18),
            modifier2: sidc.slice(18, 20),
        });
    }

    /** See {@link isSidcString}. Exposed here so `SIDC` is the one entry point. */
    static isSidcString = isSidcString;

    static tryFromString(sidc: string): SIDC | null {
        try {
            return SIDC.fromString(sidc);
        } catch {
            return null;
        }
    }

    static default(): SIDC {
        return new SIDC();
    }
}

// Wrapped exports: { record, array }

export interface SIDCData<T> {
    record: Record<string, T>;
    array: [string, T][];
}

function sidcData<T>(record: Record<string, T>): SIDCData<T> {
    return { record, array: Object.entries(record) };
}

export const AMPLIFIERS = sidcData<SIDCAmplifier>(GENERATED_SIDC_AMPLIFIERS);

// The library classifies tactical-graphic sets, including single-point control
// measures. Keep those sets out of the ordinary point-symbol picker; the generated
// catalog supplies the available codes and translations.
const MULTIPOINT_CODES: ReadonlySet<string> = getMultipointSymbolSets();

export const POINT_SYMBOL_SETS = sidcData<SIDCSymbolSet>(
    Object.fromEntries(
        Object.entries(GENERATED_SIDC_SYMBOL_SETS).filter(([code]) => !MULTIPOINT_CODES.has(code)),
    ),
);

export const MULTIPOINT_SYMBOL_SETS = sidcData<SIDCSymbolSet>(
    Object.fromEntries(
        Object.entries(GENERATED_SIDC_SYMBOL_SETS).filter(([code]) => MULTIPOINT_CODES.has(code)),
    ),
);
