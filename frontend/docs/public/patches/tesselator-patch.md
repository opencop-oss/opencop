# @deck.gl/core@9.3.1 — Tesselator patch

- **Patch file:** `patches/@deck.gl/core+9.3.1.patch`
- **Targets:** `node_modules/@deck.gl/core/src/utils/tesselator.ts`
- **Original upstream version:** 9.3.1
- **Created:** 5/25/2026
- **Last reviewed against upstream:** 5/25/26
- **Issue** Not filed

## Status

**Active — required.** No upstream issue filed.

| Date      | Upstream state                                             | Action        |
| --------- | ---------------------------------------------------------- | ------------- |
| 5/25/2026 | `_dataDiff` truncation bug present upstream; issue not filed | Patch created |

### Remove when

1. upstream truncates `vertexStarts`/`indexStarts` when `data.length` shrinks on a `_dataDiff` pass, in a released `@deck.gl/core` — verify polygon/path deletion via `_dataDiff` before deleting.

## TLDR

The `Tesselator._rebuildGeometry` wasn't truncating buffers after data length had shrunk on a `_dataDiff` pass.

## The bug being worked around

### Symptom

When deleting an element using `_dataDiff`, polygon and path layers weren't removing the specific polygon or path.

### Root cause

The `Tesselator.rebuildGeometry` method, when encountering `_dataDiff`, does not truncate `vertexStarts` or `indexStarts` if `data.length` has changed. This leads deck.gl to read leftover data in the underlying attribute buffers.

## What the patch does

### Conceptual change

Makes `vertexStarts` and `indexStarts` lengths in sync with `data.length`.

### Diff walkthrough

```diff
                     vertexStarts[dataIndex] +
                         (normalizedGeometry ? this.getGeometrySize(normalizedGeometry) : 0);
             }, startRow, endRow);
+            let length;
+            if (Array.isArray(data)) length = data.length;
+            else if (data instanceof Set || data instanceof Map) length = data.size;
+            if (length !== undefined) {
+                vertexStarts.length = length + 1;
+                indexStarts.length = vertexStarts.length;
+            }
             // count instances
             instanceCount = vertexStarts[vertexStarts.length - 1];
```

This approach only ensures datum is properly deleted given `data` is an `Array`, `Set`, or `Map`. Given that we aren't going to use `_dataDiff` on anything other than an `Array`, this works fine for our use case. Whenever an upstream deck.gl issue is files, we will certainly document this is probably not the best way to go about this and leave the architectural decision to them.
