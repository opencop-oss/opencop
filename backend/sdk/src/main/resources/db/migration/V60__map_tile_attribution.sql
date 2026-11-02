-- Credit line the Web map shows while a tile is on screen.
--
-- Vector styles usually carry their own attribution in their TileJSON; raster XYZ, WMS and WMTS
-- sources carry none, so the administrator who adds the tile supplies it here. The value is free
-- text that may contain <a href> links, as provider credits often do (for example
-- '<a href="https://maps.example.org/copyright">© Example Maps contributors</a>'). It is
-- stored with surrounding whitespace removed and checked for length only, so it is untrusted: a
-- reader reduces it to text and http(s) links before display and never inserts it as HTML. Null
-- means no credit.
ALTER TABLE map_tile
    ADD COLUMN attribution text;

-- Mirrors the API limit so a writer that bypasses Core cannot store an unbounded credit line.
ALTER TABLE map_tile
    ADD CONSTRAINT map_tile_attribution_length
        CHECK (attribution IS NULL OR char_length(attribution) <= 1000);
