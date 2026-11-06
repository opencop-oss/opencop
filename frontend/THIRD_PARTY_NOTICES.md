# Third-party notices

OpenCOP is licensed under the [Apache License 2.0](LICENSE). The third-party
material identified below retains its own copyright notices and license terms.

This document covers the derived source files, local dependency patches and
catalog data listed below. For installed dependency versions, see
[package-lock.json](package-lock.json).

## deck.gl

The following files contain material derived from deck.gl under the MIT license.
Their existing source notices describe the OpenCOP modifications.

| OpenCOP file | Upstream source |
| --- | --- |
| [diff.util.ts](packages/layers/src/diff.util.ts) | [deck.gl 9.3.1 layers utilities](https://github.com/visgl/deck.gl/blob/v9.3.1/modules/layers/src/utils.ts) |
| [mixed-billboard-multi-icon-layer-vertex.glsl.ts](packages/layers/src/mixed-billboard-text-layer/mixed-billboard-multi-icon-layer-vertex.glsl.ts) | [deck.gl 9.3.1 multi-icon vertex shader](https://github.com/visgl/deck.gl/blob/v9.3.1/modules/layers/src/text-layer/multi-icon-layer/multi-icon-layer-vertex.glsl.ts) |
| [TerrainOutlineEffect.ts](packages/layers/src/terrain-outline-extension/TerrainOutlineEffect.ts) | [deck.gl 9.3.1 terrain effect, `_updateTerrainCover`](https://github.com/visgl/deck.gl/blob/v9.3.1/modules/extensions/src/terrain/terrain-effect.ts) |
| [terrain.shader.ts](packages/layers/src/terrain-outline-extension/terrain.shader.ts) | [deck.gl 9.3.1 terrain shader module](https://github.com/visgl/deck.gl/blob/v9.3.1/modules/extensions/src/terrain/shader-module.ts) |
| [shader-module.ts](packages/layers/src/text-collision-extension/shader-module.ts) | [deck.gl 9.2.8 collision-filter shader module](https://github.com/visgl/deck.gl/blob/v9.2.8/modules/extensions/src/collision-filter/shader-module.ts) |
| [mixed-icon-layer.shader.ts](packages/scene/src/batches/icon/mixed-icon-layer.shader.ts) | [deck.gl 9.3.1 icon vertex shader](https://github.com/visgl/deck.gl/blob/v9.3.1/modules/layers/src/icon-layer/icon-layer-vertex.glsl.ts) |
| [mixed-multi-icon-layer.shader.ts](packages/scene/src/batches/text/mixed-multi-icon-layer.shader.ts) | [deck.gl 9.3.1 multi-icon vertex shader](https://github.com/visgl/deck.gl/blob/v9.3.1/modules/layers/src/text-layer/multi-icon-layer/multi-icon-layer-vertex.glsl.ts) |
| [text.util.ts](packages/scene/src/batches/text/text.util.ts) | [deck.gl 9.3.1 text-layer utilities](https://github.com/visgl/deck.gl/blob/v9.3.1/modules/layers/src/text-layer/utils.ts) |

The following patches modify deck.gl 9.3.1 packages under the same MIT license:

- [@deck.gl/core](patches/@deck.gl+core+9.3.1.patch)
- [@deck.gl/extensions](patches/@deck.gl+extensions+9.3.1.patch)
- [@deck.gl/layers](patches/@deck.gl+layers+9.3.1.patch)

See [patch documentation](docs/public/patches/) for the modifications and their
rationale. The upstream [deck.gl 9.3.1 LICENSE](https://github.com/visgl/deck.gl/blob/v9.3.1/LICENSE)
and [deck.gl 9.2.8 LICENSE](https://github.com/visgl/deck.gl/blob/v9.2.8/LICENSE)
contain the following text:

```text
Copyright Vis.gl contributors.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
```

## luma.gl

The [@luma.gl/shadertools 9.3.3 patch](patches/@luma.gl+shadertools+9.3.3.patch)
modifies luma.gl under the MIT license. See the
[fragment output location patch documentation](docs/public/patches/fragment-output-location-patch.md)
for the modification and rationale.

The full upstream [luma.gl 9.3.3 LICENSE](https://github.com/visgl/luma.gl/blob/v9.3.3/LICENSE),
including its additional attributions, follows:

```text
luma.gl is provided under the MIT license

Copyright (c) 2020 vis.gl contributors

This software includes parts initially developed by Uber and open sourced under MIT license.
Copyright (c) 2015 Uber Technologies, Inc.

This software includes parts of PhiloGL (https://github.com/philogb/philogl)
under MIT license. PhiloGL parts Copyright © 2013 Sencha Labs.

This software includes adaptations of some postprocessing code from
THREE.js (https://github.com/mrdoob/three.js/) under MIT license.
THREE.js parts Copyright © 2010-2018 three.js authors.

Additional attribution given in specific source files.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.

```

## Esri joint military symbology catalog

[packages/symbology/src/sidc.generated.ts](packages/symbology/src/sidc.generated.ts)
is generated by [scripts/generate-sidc-catalog.mjs](scripts/generate-sidc-catalog.mjs)
from Esri's [joint-military-symbology-xml](https://github.com/Esri/joint-military-symbology-xml/tree/094e7647f0bdd001e42fd2a73ba802c995af20aa)
at revision `094e7647f0bdd001e42fd2a73ba802c995af20aa`.

Copyright 2014 Esri. Licensed under the
[Apache License 2.0](https://github.com/Esri/joint-military-symbology-xml/blob/094e7647f0bdd001e42fd2a73ba802c995af20aa/license.txt).
A copy of the Apache License 2.0 is included in [LICENSE](LICENSE).
The generated file retains the upstream notice and records the archive hash and
OpenCOP transformations: selecting labels and SIDC relationships, omitting unused
XML metadata and the internal symbol set, deriving locale keys, preserving the
specified label/optional-field behavior, and emitting deterministic TypeScript
lookup records.
