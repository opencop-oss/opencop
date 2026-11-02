# @deck.gl/layers@9.3.1 — Font atlas patch

- **Patch file:** `patches/@deck.gl+layers+9.3.1.patch`
- **Targets:** `node_modules/@deck.gl/layers/src/text-layer/font-atlas-manager.ts`
- **Original upstream version:** 9.3.1
- **Created:** 6/16/2026
- **Last reviewed against upstream:** 6/16/26
- **Issue** Not filed

## Status

**Active — required.** No upstream issue filed — nothing tracks this upstream yet.

| Date      | Upstream state                                  | Action        |
| --------- | ----------------------------------------------- | ------------- |
| 6/16/2026 | No eviction/reset API upstream; issue not filed | Patch created |

### Remove when

1. deck ships a font-atlas eviction/reset API (migrate `RecyclingTextLayer`'s `deleteAtlas()` call first), **or**
2. the atlas stops being an unbounded module-level cache.

## TLDR

The SDF font atlas never evicts, so a long session with many distinct CJK label glyphs overflows
the GPU texture and every label renders solid white. This adds a `deleteAtlas()` so we can evict and
rebuild the atlas from only the live glyphs.

## The bug being worked around

### Symptom

After a long session with many distinct **CJK** label glyphs, every map label renders as a solid
white bar (all at once).

### Root cause

deck's SDF font atlas is a module-level LRU cache keyed by **font config only**
(`fontFamily fontWeight fontSize buffer radius cutoff`) — never the character set. With
`characterSet: "auto"`, every distinct glyph ever seen is appended to a 1024-px-wide canvas whose
height only ever grows. Latin saturates a few hundred glyphs (~256px); CJK has thousands, so over a
long session the atlas climbs past the GPU `MAX_TEXTURE_SIZE` (Firefox 8192). Once it overflows the
texture allocation fails, and the SDF shader reads the incomplete texture's alpha as `1.0`
everywhere → every label quad fills solid with the text colour. It is one shared texture, so the
failure is all-or-nothing. deck exposes no API to evict or reset the atlas.

## What the patch does

### Conceptual change

Add a `deleteAtlas()` method to `FontAtlasManager` that evicts this manager's atlas from the shared
module-level `LRUCache` (which already has `delete()`) and drops the local reference, so the next
`setProps` rebuilds a fresh atlas from only the currently-live `characterSet`. The atlas is keyed by
the real, untouched font config — no `fontSize`/`fontFamily` perturbation — so glyphs render
pixel-identically and the prop contract is honored.

### Diff walkthrough

```diff
     // update cache
     cache.set(this._key, fontAtlas);
   }

+  deleteAtlas(): void {
+    if (this._key !== undefined) {
+      cache.delete(this._key);
+    }
+    this._atlas = undefined;
+  }
+
   // eslint-disable-next-line max-statements
   private _generateFontAtlas(characterSet: Set<string>, cachedFontAtlas?: FontAtlas): FontAtlas {
```

`cache` is the module-level `LRUCache`; `this._key` / `this._atlas` are the manager's current cache
key and atlas. Consumed by `RecyclingTextLayer` (`packages/layers/src/recycling-text-layer/`), which
calls `deleteAtlas()` when the atlas is under pressure and then lets deck's own `_updateFontAtlas`
rebuild. Every live label layer inherits this through `MixedBillboardTextLayer → BinaryTextLayer →
CulledTextLayer`, so it applies to every `*-text` sublayer at one chokepoint.

Remove this patch if deck ships a font-atlas eviction/reset API, or the atlas stops being an
unbounded module-level cache.
