# OpenCOP demo limitations

This is a local evaluation environment for seeing OpenCOP run on one machine.
It keeps its data when stopped and resumed until you reset it, but it is not a
development workflow or a production deployment. Start with the
[demo README](README.md). Production hosting architecture and operations are
outside this demo's scope and remain the operator's responsibility.

- [Lifecycle and reset](#lifecycle-and-reset)
- [Persistence and the static seal key](#persistence-and-the-static-seal-key)
- [Default map and credits](#default-map-and-credits)
- [Credentials and secret delivery](#credentials-and-secret-delivery)
- [Local HTTP exposure](#local-http-exposure)
- [Single-node topology and operations](#single-node-topology-and-operations)
- [Source builds and repository layout](#source-builds-and-repository-layout)
- [External dependency substitutions](#external-dependency-substitutions)
- [Identity bootstrap](#identity-bootstrap)
- [Platform boundary](#platform-boundary)

## Lifecycle and reset

**Demo behavior.** OpenBao keeps the secrets Core writes at runtime, such as connection credentials, mTLS material, and map-tile tokens, on the `openbao-data` volume. It initializes itself on the first start against an empty volume, creating the fixed token that Core and Connect read, and unseals itself with a static key on every later start. `docker compose stop`, plain `docker compose down`, and `docker compose up` keep this data together with the other services' volumes. `docker compose down -v` deletes all of it, including OpenBao's data and the default map and banners.

**Practical consequence.** Pause and resume the demo with `docker compose stop` and `docker compose up --wait --wait-timeout 900`. Reset it completely with:

    docker compose down -v
    docker compose up --build --wait --wait-timeout 900

Older versions of this demo kept OpenBao in memory. After upgrading from one, reset once with `docker compose down -v`; volumes from the older demo can hold records whose secrets are already gone. If the very first launch is interrupted while OpenBao initializes itself, the volume can be left unusable, for example without the fixed token; reset with `docker compose down -v` in that case too.

**Production responsibility.** A durable deployment needs real secret custody,
controlled initialization and unsealing procedures, scoped workload
identities, and a tested backup and recovery procedure. The demo's
self-initialization and static seal key are a convenience for a disposable
local host, not a production pattern.

## Persistence and the static seal key

**Demo behavior.** PostgreSQL, Redis, Kafka, Azurite, Keycloak, and OpenBao
state use Compose volumes on the local Docker host. OpenBao's storage is
encrypted with a static seal key that `compose.yaml` contains as a published,
demo-only value. The demo has no backup, restore, or reconciliation.

**Practical consequence.** The static seal key protects nothing: anyone with the source and the volume can read the secrets stored there. Never reuse the key, and never put real credentials in the demo. OpenBao cannot read existing data with a different key, so the key cannot be changed without `docker compose down -v`. Removing or recreating only some volumes can leave durable records that refer to missing secrets; reset the whole demo instead.

**Production responsibility.** Persistent services and secret storage need a
coordinated backup, restore, and reconciliation design, and unseal material
must stay out of source control. Operators must define and test recovery for
the complete deployment rather than assuming that retained volumes form a
consistent generation.

## Default map and credits

**Demo behavior.** A one-shot job adds the OpenFreeMap `dark` basemap as the primary, globally visible map tile, a header banner that marks the evaluation demo, and a footer banner with the map credits: OpenFreeMap, © OpenMapTiles.org, and data © OpenStreetMap contributors, linked to <https://www.openstreetmap.org/copyright>. Users cannot dismiss either banner; an administrator can edit or delete them. It runs once per database, records that in a `demo_seed` schema outside the application's migrations, and leaves later administrator changes alone.

**Practical consequence.** The basemap loads in the browser from the public OpenFreeMap service, served through Cloudflare, so the map needs internet access. The service needs no key, is provided as-is, and may change or stop without notice. OpenCOP's map turns off MapLibre's attribution control, so the tile source's own credits do not show, and the footer banner is the only on-map credit.

**Production responsibility.** Choose a basemap provider whose terms,
availability, and privacy properties fit the deployment, and keep its
required attribution visible.

## Credentials and secret delivery

**Demo behavior.** `compose.yaml` contains fixed development credentials and
tokens so the bundled demo can launch with one standard Compose command and no
environment file. Some startup values are passed through the environment and
seeded into OpenBao; runtime product secrets are written through the
application's `SecretStore`. Core and Connect mount the same fixed OpenBao
root token. The four Java containers also share several startup values
required by the current application property model, including external Event
Hubs client and management settings, even though Core performs the management
operations. This dev-only dual-secret model and broad trusted-container
boundary are deliberate.

**Practical consequence.** The checked-in values are public local-demo
defaults, not safe deployment defaults. Never commit customer, production, or
personal cloud credentials. If testing external services, use dedicated,
disposable, least-privileged identities and assume their values are available
to multiple trusted demo containers. Shell variables or an ignored local
`.env` are only convenient forms of ordinary Compose interpolation; they are
not encrypted secret storage. Only the complete override bundles documented in
the README are supported; changing one side of a bundled dependency is not.
Per-service production identities and narrowing Event Hubs runtime properties
from Core-only management properties require application and deployment
changes outside this single-file demo.

**Production responsibility.** Keep environment-specific configuration outside
committed source, use a suitable secret-management system, give workloads
separate least-privileged identities, and define credential rotation and
revocation. This demo's embedded defaults are not suitable starting values.

## Local HTTP exposure

**Demo behavior.** Published endpoints bind to `127.0.0.1`, use HTTP, and have a fixed `http://localhost` browser origin. The demo has no configurable public hostname, certificates, HTTPS router, public hostname hardening, or non-localhost secure-cookie deployment contract. Traefik mounts the Docker daemon socket to discover this stack; the bind is filesystem-read-only, but Docker API access is not restricted to read operations.

**Practical consequence.** Do not expose it to an untrusted network or use it as an internet-facing deployment. Run only trusted source and images on a trusted development or disposable Docker host: compromise of the Traefik container can extend to other containers and the host through the daemon socket. Traefik constrains route discovery to the effective Compose project so labeled containers from neighboring local stacks cannot enter this demo's routing table; the constraint does not reduce the daemon-socket privilege boundary.

**Production responsibility.** Internet-facing hosting requires an
operator-owned ingress design, TLS certificates and renewal, hostname and
cookie hardening, network controls, and a deliberate Docker API exposure
boundary. Do not infer those controls from this local router.

## Single-node topology and operations

**Demo behavior.** The stack runs one instance of each Java service, one Kafka broker/controller with automatic topic creation, and one instance of each supporting service. It intentionally omits high availability, rolling deployment, automatic recovery, backups, an observability stack, and administrative tools.

**Practical consequence.** A healthy launch proves the product can start and be explored; it does not demonstrate resilience, capacity, upgrade safety, disaster recovery, or production operability. Apart from the default basemap and banners, the authenticated map begins without seeded tracks, missions, features, or other scenario data.

**Production responsibility.** Define availability and capacity targets,
replica and rollout behavior, monitoring and alerting, log retention, backup
and recovery, upgrade safety, and incident procedures. A successful demo
launch supplies evidence for none of those operating objectives.

## Source builds and repository layout

**Demo behavior.** From the `demo/` directory, Compose builds OpenCOP
application images from the included `backend/` and `frontend/` source trees.
It builds demo-local infrastructure images from inline Dockerfiles,
uses project-scoped local image names, and does not pull published OpenCOP
application images.

**Practical consequence.** Evaluators must preserve the included repository layout, provide substantial Docker memory and disk, and wait for a potentially long first build. This is less conventional than an OSS quick start based on public images, but public binary/image distribution is outside this work item.

**Production responsibility.** A deployable release normally needs versioned
and provenance-checked images, a controlled registry, vulnerability and policy
checks, and a rollback strategy. Building application source on the demo host
is not a production release process.

## External dependency substitutions

**Demo behavior.** The README documents the existing OpenCOP configuration
paths for Azure Event Hubs, Azure Blob Storage, a compatible standalone Redis
deployment, and PostgreSQL/PostGIS. Replacing a bundled dependency requires
providing environment overrides and editing `compose.yaml`; this file does not
abstract those choices into profiles or a wizard. OpenBao and MediaMTX stay
bundled.

**Practical consequence.** The PostgreSQL, Azure Blob Storage, and Azure Event Hubs substitutions were exercised end to end for this demo; the PostgreSQL run predates the default-content job, so see the README's PostgreSQL step 6. A faithful external Redis substitution was not: creation of the required classic Azure Cache for Redis topology was blocked by the service's retirement policy, and Azure Managed Redis was not treated as an equivalent substitute. OpenCOP currently requires standalone, non-clustered Redis because its Lua scripts and multi-key operations are not cluster-safe. Clustered Redis support is planned as a near-term improvement.

`docker compose down -v` cannot clear substituted services. External PostgreSQL/Keycloak and Redis state can outlive the OpenBao data that `down -v` deletes, and an external database keeps its default-content marker, so a reset does not add the default map and banners again. Blob objects require separate cleanup, and Event Hubs sources own dynamic hubs and send rules. Use dedicated disposable dependencies or perform operator-owned cleanup; delete UI-created Event Hubs sources while Core and its management identity are still available.

**Production responsibility.** Operators own provisioning, compatibility,
network access, identities, lifecycle, and cleanup for every substituted
dependency. This demo is not a general infrastructure-selection or deployment
tool.

## Identity bootstrap

**Demo behavior.** A fixed `demo` user and minimum browser client are created
in stock Keycloak. The initializer then provisions the matching OpenCOP user
directly through Core's SCIM endpoint. The demo does not install the custom
Keycloak SCIM event listener or branded login theme. Identity-related defaults
and environment overrides are bootstrap inputs, not continuously reconciled
configuration.

**Practical consequence.** Creating a user later through Keycloak does not
propagate that user to Core. Changing the username, password, default
administrator, SCIM token, or related Keycloak settings on an established
session is unsupported. Reset all demo volumes before relaunching. This demo
is not documentation for integrating an external identity provider.

**Production responsibility.** Operators must design and test the target
identity-provider integration, claim mapping, user lifecycle, provisioning,
authorization, credential policy, and recovery. None should be inferred from
the bundled demo identity.

## Platform boundary

**Demo behavior.** The stack targets Linux containers, Compose 2.36.2 or newer, Buildx, and the default `/var/run/docker.sock`. The full demo path has been exercised with Docker Desktop on Apple silicon and with Docker Engine on native x86-64 Ubuntu 24.04 LTS. A separate forced `linux/amd64` build and runtime smoke on Apple silicon used emulation and is not evidence of a native x86-64 Linux host. The restartable OpenBao storage and the default map content were exercised on Docker Desktop for macOS.

**Practical consequence.** Windows Docker Desktop with WSL2 should follow the same Linux-container model but is not validated here. Native Windows containers, rootless Docker, alternate daemon sockets, and other container engines are out of scope.

**Production responsibility.** Operators own platform selection, operating
system and container-engine hardening, patching, supported architectures,
resource isolation, and platform-specific validation.

For the runnable happy path, return to the [demo README](README.md).
