# OpenCOP

OpenCOP is a web-based common operating picture that brings live data and
geospatial context together in one shared view. Connect data sources,
explore events on the map, and review how the picture changes over time.

- **Live map updates** — Follow incoming data as objects and events change.
- **Maps and layers** — Organize information into layers, switch basemaps,
  and explore 3D terrain.
- **Visual data mapping** — Map fields from CSV, JSON, and XML data into
  geographic features.
- **History and playback** — Review past events and replay object movement.

## Try OpenCOP

This repository contains the OpenCOP application source and a Docker Compose
demo for local evaluation. Production infrastructure and deployment
automation are not included.

From the root of your cloned repository:

```bash
cd demo
docker compose up --build --wait --wait-timeout 900
```

Once startup completes, open <http://localhost> and sign in with username
`demo` and password `opencop-demo`.

You'll need Docker with Compose 2.36.2 or newer and Buildx, at least 12 GB of
memory available to Docker, and about 40 GB of free disk space. The first
source build can take 10–25 minutes.

See the [demo guide](demo/README.md) for full prerequisites, stop and reset
commands, troubleshooting, and limitations.

## Source code

- [`backend/`](backend/) — Java backend services.
- [`frontend/`](frontend/) — Web application.
- [`demo/`](demo/) — Docker Compose evaluation stack.

Licensed under the [Apache License 2.0](LICENSE).
