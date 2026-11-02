export type DataRange = {
    startRow: number;
    endRow: number;
};

export type DataChangedRange = {
    startRow: number;
    endRow?: number;
};

/** An array containing data for a sub layer and its diff. */
export type SubLayerArray<T> = {
    data: T[];
    diff: DataRange[] | null;
};

/** Object containing {@link SubLayerArray SubLayerArray}, used by {@link https://deck.gl/docs/developer-guide/custom-layers/composite-layers#collection-layers Collection Layers}. */
export type Collection = Record<string, SubLayerArray<unknown>>;
/** Raw data belonging to sub layers of a {@link https://deck.gl/docs/developer-guide/custom-layers/composite-layers#collection-layers Collection Layer}. */
export type CollectionData<C extends Collection> = {
    [K in keyof C]: C[K]["data"];
};

/**
 * Replace the rows of one {@link Collection} with another.
 *
 * @param collection The original {@link Collection}.
 * @param build A function to build a new {@link Collection} for the changed rows.
 * @param dataChanged The array of rows that have been changed, passed to {@link build}.
 * @returns A new {@link Collection} with the diffed data.
 */
export function diffCollection<C extends Collection>(
    collection: C,
    build: (range: DataChangedRange) => C,
    dataChanged: DataChangedRange[],
): C {
    const newCollection = Object.fromEntries(
        Object.entries(collection).map(([key, value]) => [
            key,
            { data: value.data.slice(), diff: [] as DataRange[] },
        ]),
    ) as C;

    dataChanged.forEach((dataRange) => {
        const replacement = build(dataRange);
        Object.entries(newCollection).forEach(([key, array]) => {
            const oldLength = array.data.length;
            const derivedRange = replaceInRange({
                data: array.data,
                getIndex: (d) => (d as { __source: { index: number } }).__source.index,
                dataRange,
                replace: replacement[key].data,
            });

            // Deck.gl's replaceInRange returns startRow = endRow if the array shrinks,
            // we need sublayers to be aware of the rows that have been removed.
            const shrink = Math.max(0, oldLength - array.data.length);
            array.diff?.push({
                startRow: derivedRange.startRow,
                endRow: derivedRange.endRow + shrink,
            });
        });
    });

    return newCollection;
}

/**
 * Derived from deck.gl v9.3.1:
 * https://github.com/visgl/deck.gl/blob/v9.3.1/modules/layers/src/utils.ts
 *
 * MIT License
 * Copyright Vis.gl contributors.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 * OpenCOP modifications: generic typing for derived arrays, expanded
 * input-row/derived-position contract documentation, and binary-search range
 * lookup while preserving in-place replacement and returned range semantics.
 *
 * Splice a partial-diff range into a derived array, translating from
 * **input-data row coordinates** to **derived-array position coordinates**.
 *
 * Composite layers often hold a derived array in `state` that doesn't map 1:1
 * to the user's input data.
 *
 * Each derived item carries `__source.index` — the input-data row it came
 * from. The derived array stays **sorted** by that index.
 *
 * deck.gl's `_dataDiff` mechanism reports change ranges in **input-data
 * row** coordinates ("rows 50..60 changed"). To apply that diff to a
 * derived array, the layer needs to:
 *
 *   1. Find the slice of the derived array whose `__source.index` falls in
 *      `[startRow, endRow)`.
 *   2. Replace that slice with newly-computed entries for those rows.
 *   3. Shift the tail if the new entries don't have the same count as the
 *      old ones (e.g. a row went from 4 ring entries to 6).
 *
 * @param data        Derived array, sorted by `getIndex`. Mutated in place.
 * @param getIndex    Reads the input-data row index from a derived item.
 *                    Must be {@link https://en.wikipedia.org/wiki/Monotonic_function strictly increasing} across `data`.
 * @param dataRange   The diff range in **input-data row** coordinates.
 * @param replace     Newly-computed derived entries for that range.
 * @returns           `{startRow, endRow}` **derived-array positions** —
 *                    the actual slice of `data` that was rewritten.
 */
export function replaceInRange<T>({
    data,
    getIndex,
    dataRange,
    replace,
}: {
    data: T[];
    getIndex: (d: T) => number;
    dataRange: { startRow?: number; endRow?: number };
    replace: T[];
}): { startRow: number; endRow: number } {
    const { startRow = 0, endRow = Infinity } = dataRange;
    const count = data.length;

    // First index where getIndex(data[i]) >= startRow.
    let lo = 0;
    let hi = count;
    while (lo < hi) {
        const mid = (lo + hi) >>> 1;
        if (getIndex(data[mid]) < startRow) lo = mid + 1;
        else hi = mid;
    }
    const replaceStart = lo;

    // First index where getIndex(data[i]) >= endRow (starting from replaceStart).
    lo = replaceStart;
    hi = count;
    while (lo < hi) {
        const mid = (lo + hi) >>> 1;
        if (getIndex(data[mid]) < endRow) lo = mid + 1;
        else hi = mid;
    }
    const replaceEnd = lo;

    let index = replaceStart;
    const dataLengthChanged = replaceEnd - replaceStart !== replace.length;
    // Save the items after replaceEnd before we overwrite data
    const endChunk = dataLengthChanged ? data.slice(replaceEnd) : undefined;
    // Insert new items
    for (let i = 0; i < replace.length; i++) {
        data[index++] = replace[i];
    }
    if (endChunk) {
        // Append items after replaceEnd
        for (let i = 0; i < endChunk.length; i++) {
            data[index++] = endChunk[i];
        }
        // Trim additional items
        data.length = index;
    }

    return {
        startRow: replaceStart,
        endRow: replaceStart + replace.length,
    };
}

export function dataDiff(dataRanges: DataRange[] | null): (() => DataRange[]) | undefined {
    return dataRanges ? () => dataRanges : undefined;
}
