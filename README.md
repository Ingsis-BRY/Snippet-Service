# Snippet Service

A Kotlin / Spring Boot service that manages code snippets.


- [API](#api)
- [Architecture](#architecture-at-a-glance)
- [Decisions and tradeoffs](#decisions-and-tradeoffs)
  - [1. Hexagonal architecture](#1-hexagonal-architecture)
  - [2. Domain / application / adapter split](#2-domain--application--adapter-split)
  - [3. Light CQRS](#3-light-cqrs)
  - [4. The Result pattern](#4-the-result-pattern)
  - [5. Two stores, no shared transaction](#5-two-stores-no-shared-transaction)
  - [6. Cleanup sweep instead of an outbox](#6-cleanup-sweep-instead-of-an-outbox)
  - [7. Blob storage is an adapter, not an asset-service](#7-blob-storage-is-an-adapter-not-an-asset-service)
  - [8. Data races](#8-data-races)
  - [9. Error handling](#9-error-handling)
  - [10. Filters as a plugin architecture](#10-filters-as-a-plugin-architecture)
  - [11. Validation lives in the domain](#11-validation-lives-in-the-domain)
  - [12. Language rules live behind ports](#12-language-rules-live-behind-ports)
  - [13. Smaller decisions](#13-smaller-decisions)
- [Known limitations](#known-limitations)
- [Testing](#testing)

---

## Quick start

Requirements: JDK 21, Docker. The build uses convention plugins published to GitHub Packages, so Gradle
needs credentials in the `USERNAME` and `TOKEN` environment variables (or `gpr.user` / `gpr.key`
Gradle properties).

```bash
# build + tests + ktlint + detekt
./gradlew check

# local settings: copy the template, then fill in the Auth0 M2M credentials
cp .env.example .env

# 1. the language engine first: its compose creates the `snippet-searcher` network and the
#    `engine-proxy` this service validates and runs code through
(cd ../printscript-engine && GITHUB_ACTOR=<github-user> GITHUB_TOKEN=<github-token-with-read:packages> docker compose up -d --build)

# GitHub Packages credentials for the image build (or set USERNAME / TOKEN in .env)
export USERNAME=<github-user> TOKEN=<github-token-with-read:packages>

# 2. everything else (service, Postgres, Azurite, permission service and its database)
docker compose up --build
```

`docker compose up` fails with `network snippet-searcher declared as external, but could not be found` when
the engine's compose isn't up. To check that the service reaches the engine:

```bash
docker compose exec snippet-service sh -c 'wget -qO- --header "X-Language: printscript" http://engine-proxy/versions'
```

`USERNAME` and `TOKEN` are required for `docker compose up --build`: they become the `github_username` /
`github_token` build secrets. On Windows `USERNAME` is already defined as your Windows user, so set it
explicitly to your GitHub user.

`docker compose` reads these variables (`.env.example` lists all of them with local defaults):

| Variable | Purpose |
| --- | --- |
| `SNIPPET_SERVICE_PORT` | Host port for the service |
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | Database credentials |
| `AZURE_STORAGE_CONNECTION_STRING` | Blob storage connection string (Azurite or real Azure) |
| `AZURITE_PORT` | Host port for Azurite's blob endpoint |
| `PERMISSION_POSTGRES_DB`, `PERMISSION_POSTGRES_USER`, `PERMISSION_POSTGRES_PASSWORD` | Permission database credentials |
| `PERMISSION_SERVICE_IMAGE` | Optional, defaults to `ghcr.io/ingsis-bry/snippet-permission-service:latest` |
| `AUTH0_DOMAIN`, `AUTH0_AUDIENCE` | Auth0 tenant and API identifier, for this service and the permission service |
| `AUTH0_M2M_CLIENT_ID`, `AUTH0_M2M_CLIENT_SECRET` | Machine-to-machine app allowed to read users (`read:users`), for the user search |
| `AUTH0_CLI_CLIENT_ID` | Only for `scripts/get-token.mjs`: the native test app |
| `ENGINE_URL` | Optional, defaults to `http://engine-proxy:80`, the engine proxy's service name in compose and Swarm. Outside Docker use `http://localhost:8081` |
| `USERNAME`, `TOKEN` | GitHub Packages credentials, used as Docker build secrets |

The service itself reads `PERMISSION_SERVICE_URL` (default `http://snippet-permission-service:8080`, the
service name inside the Swarm and compose networks), `ENGINE_URL` (default `http://engine-proxy:80`; the
WebSocket URL is derived from it, `http` → `ws`, `https` → `wss`) and `DB_HOST` / `DB_PORT` (default
`localhost:5432`, for running the jar outside Docker). The compose file sets `DB_HOST=postgres` so the container reaches the
compose database. Inside compose, `AZURE_STORAGE_CONNECTION_STRING` must use
`BlobEndpoint=http://azurite:10000/devstoreaccount1`, as in `.env.example`.

Optional property: `snippet.orphaned-content-cleanup.cron` (default `0 0 3 * * *`, daily at 03:00).

The `.env` file is gitignored; never commit it, because it holds the machine-to-machine secret.

### Getting an access token

`scripts/get-token.mjs` opens the Auth0 login page, logs in with Authorization Code + PKCE through the
`Snippet Searcher CLI (tests)` application and prints the access token (Node 20+, reads `.env`):

```bash
TOKEN=$(node scripts/get-token.mjs)                    # read:snippets write:snippets
curl -H "Authorization: Bearer $TOKEN" localhost:8080/snippets
```

```powershell
$env:TOKEN = node scripts/get-token.mjs
curl.exe -H "Authorization: Bearer $env:TOKEN" localhost:8080/snippets
```

## API

Every endpoint requires an Auth0 access token for the API `https://snippet-searcher-api`, in
`Authorization: Bearer <token>`. The signature, issuer, audience and expiry are validated; the caller is
the token's `sub` (for example `auth0|6afb5dfa6b2193fbf0b82627`).

- `GET` endpoints need the `read:snippets` scope, everything else `write:snippets`. Both come from the
  Auth0 role `snippet-user`.
- Without a valid token the answer is `401`; with a token that lacks the scope, `403`.

| Method | Path | Description |
| --- | --- | --- |
| `POST` | `/snippets` | Create a snippet (version 1). Returns `201` with the id. |
| `GET` | `/snippets` | Paginated list. Params: `page`, `size` (1-100), `sortBy` (`name`, `language`, `valid`), `direction`, `relationship`, plus the filters `name`, `language`, `valid`. |
| `GET` | `/snippets/{id}` | Snippet with its current version's content. |
| `PUT` | `/snippets/{id}` | Replace name/description/language/content; creates a new version. |
| `PATCH` | `/snippets/{id}` | Replace only the content (body `{"content": ...}`); creates a new version, other fields are kept. |
| `DELETE` | `/snippets/{id}` | Delete the snippet, all versions and their blobs. |
| `GET` | `/snippets/{id}/download?version=original\|formatted` | Content as a `text/plain` attachment. |
| `POST` | `/snippets/upload` | Create a snippet from an uploaded file. `multipart/form-data` with `name`, `description`, `language`, `languageVersion` and `file`. Returns `201` with the id. |
| `PUT` | `/snippets/{id}/upload` | Replace name/description/language/content from an uploaded file. Same form fields as above; creates a new version. |
| `PATCH` | `/snippets/{id}/upload` | Replace only the content from an uploaded file. `multipart/form-data` with `file`; creates a new version, other fields are kept. |
| `POST` | `/snippets/{id}/share` | Share the snippet with another user (body `{"targetUserId": "auth0\|..."}`). Only the owner. Returns `204`. |
| `GET` | `/snippets/{id}/run` | **WebSocket.** Interactive run of the snippet's current version (#10). See [Interactive run](#interactive-run-websocket). |
| `GET` | `/users?name=&page=0&size=10` | Users to share with (#7): name or email contains `name` (prefix if shorter than 3 characters), never the caller. Returns `{users: [{id, name}], page, size, total}`. |

Who can do what is decided by `snippet-permission-service`:

| Endpoint | Required permission |
| --- | --- |
| Create (JSON or file) | none; the caller becomes the `OWNER` |
| Get, download, run (WebSocket) | `READ` (owner or shared with) |
| Update, patch (JSON or file) | `WRITE` (owner) |
| Delete | `DELETE` (owner) |
| Share | `SHARE` (owner) |
| List | only snippets the caller can access; `relationship=OWNED\|SHARED\|ALL` |

- The three `/upload` endpoints exist because the assignment's user stories #1 and #2 require creating and
updating snippets through file uploads. 
- They reuse the same use cases as the JSON endpoints, so the same
validation and versioning apply. 
- The file must be non-empty UTF-8 text, otherwise the response is `400`.

### Invalid code

Create and update (`POST /snippets`, `POST /snippets/upload`, `PUT|PATCH /snippets/{id}` and
`PUT|PATCH /snippets/{id}/upload`) ask the engine to validate the content first (#1-#4). Code that breaks a
rule is rejected before anything is stored:

```json
HTTP/1.1 400  Content-Type: application/problem+json
{
  "title": "InvalidSnippetContent",
  "status": 400,
  "detail": "Snippet breaks the rule 'UnexpectedToken' at line 1, column 17: Expected an expression",
  "instance": "/snippets",
  "rule": "UnexpectedToken",
  "message": "Expected an expression",
  "span": { "start": { "line": 1, "column": 17 }, "end": { "line": 1, "column": 17 } }
}
```

- Positions are 1-based and inclusive. `span` is `null` when the engine has no position (e.g. `NestingTooDeep`),
  and then `detail` reads `Snippet breaks the rule '<rule>': <message>`.
- `type` is left out, which means `about:blank` (RFC 9457).
- A language or version the engine doesn't support is `400` with title `UnsupportedLanguage`.
- An engine that can't be reached, or answers 502/503/504 twice, is `503` with detail
  `"Language service is unavailable"`.

### Interactive run (WebSocket)

US #10. The browser opens `wss://<host>/api/snippets/{snippetId}/run`; nginx strips `/api`, so this service
serves `/snippets/{snippetId}/run`. It relays the run to the engine's `/run/interactive` socket.

**Handshake.** Browsers can't set `Authorization` on a WebSocket, so the token travels as a subprotocol:

```js
new WebSocket(url, ["snippet-run.v1", "bearer." + accessToken])
```

- The server answers `Sec-WebSocket-Protocol: snippet-run.v1`.
- The token never appears in a URL, so it never reaches the nginx access or error logs.
- A missing or invalid token, or one without `read:snippets`, is refused with `401`/`403` **before** the upgrade:
  the browser sees `error` and then `close` 1006 without any `open`.
- READ on the snippet is checked during the handshake, and its current content and `languageVersion` are
  loaded then. **The client never sends code or a version.**

**Client → server** (JSON text frames):

| Message | Meaning |
| --- | --- |
| `{"type":"start"}` or `{"type":"start","mode":"interactive"}` | Starts a run. The first `start` opens the engine socket; after a `done`, another `start` re-runs on the same connection. Any other `mode` gets `rejected` / `MalformedMessage`. |
| `{"type":"input","value":"<string>"}` | Answers the prompt. Answers sent early are queued by the engine. |

**Server → client:**

| Message | Source |
| --- | --- |
| `{"type":"output","line":"..."}` | Relayed from the engine. |
| `{"type":"awaiting-input","prompt":"..."}` | Relayed from the engine. |
| `{"type":"done","outputs":["..."],"error":null}` | Relayed from the engine; exactly one per run. `error` is `{rule, message, span}` (`span` can be `null`): a runtime error, `ExecutionTimeout`, `InputIdleTimeout`, `OutputLimitExceeded`, ... |
| `{"type":"rejected","title":"...","detail":"...","properties":{}}` | From the engine (`RunInProgress`, `NoInteractiveRun`, `TooManyInputs`, `UnsupportedVersion`, ...) or from this service (`MalformedMessage`, `NoInteractiveRun` before any `start`). The connection stays open. |
| `{"type":"error","status":403,"title":"AccessDenied","detail":"..."}` | From this service, **always followed by a close** with code `4000 + status` and the title as the reason. |

| Condition | status / title | Close code |
| --- | --- | --- |
| No READ permission (or the snippet doesn't exist for the user) | 403 `AccessDenied` | 4403 |
| Permission OK but the row is missing | 404 `SnippetNotFound` | 4404 |
| The id is not a UUID | 400 `InvalidInput` | 4400 |
| Engine unreachable or dropped; permission service, storage or DB down | 503 `LanguageServiceUnavailable` / `PermissionServiceUnavailable` / `ContentStoreUnavailable` / `PersistenceUnavailable` | 4503 |
| `ContentMissing`, `SnippetCorrupted`, or an unexpected exception (`InternalError`) | 500 | 4500 |

**Closing and timeouts.**
- A client close (any code) closes the engine socket, which cancels the run. The UI closes with 1000 after `done`.
- If the engine closes unexpectedly, the client gets the 503 error frame and close 4503.
- The engine limits a run to 5 s of CPU time (time spent waiting for input doesn't count) and each input wait to
  5 min; both end with a normal `done` that carries the error.
- Sessions idle for more than 360 s are closed (`maxSessionIdleTimeout`), as nginx's `proxy_read_timeout`.
- Connecting to the engine waits up to 5 s and is retried once.

Try it with [wscat](https://github.com/websockets/wscat):

```bash
npx wscat -c ws://localhost:8080/snippets/<id>/run -s snippet-run.v1 -s "bearer.$TOKEN"
> {"type":"start"}
```

## Architecture

```
com.ingsisbry.snippet
├── domain/                     Snippet, SnippetVersion, SnippetRules   (pure Kotlin)
├── application/
│   ├── command/                create, update, delete, share, cleanup, run  (write side use cases)
│   ├── query/                  get, list, download, user search        (read side use cases)
│   ├── port/out/               SnippetRepository, SnippetQueryRepository,
│   │                           SnippetContentStorage, SnippetFilterValidator,
│   │                           SupportedLanguages, SnippetContentValidator,
│   │                           SnippetPermissions, UserDirectory, InteractiveRunner
│   ├── error/                  sealed SnippetError
│   └── common/                 Result, PageResult
└── adapter/
    ├── web/                    controller, DTOs, error → HTTP mapping
    ├── persistence/            JPA entities, repositories, filters, specifications
    ├── storage/azure/          blob storage adapter
    ├── language/               HTTP and WebSocket client for the engine proxy
    ├── run/                    run WebSocket for the browser (handshake, relay)
    ├── permission/             HTTP client for snippet-permission-service
    ├── auth0/                  user search in the Auth0 Management API
    ├── security/               JWT validation, scopes, current user, token forwarding
    ├── scheduling/             orphan cleanup job
    └── configuration/          Clock bean, scheduling
```

Dependencies point inward only: `adapter → application → domain`.

```
                    HTTP                                         PostgreSQL
                      │                                               ▲
                      ▼                                               │
  ┌──────────────────────────────────────────────────────────────────────────┐
  │ adapter.web ──► application (use cases) ──► port.out ◄── adapter.persistence │
  │                         │                       ▲                            │
  │                         ▼                       └──── adapter.storage.azure ─┼──► Blob storage
  │                      domain                                                  │
  └──────────────────────────────────────────────────────────────────────────┘
```

---

## Decisions and tradeoffs

### 1. Hexagonal architecture

**Decision.** Use cases depend on interfaces (*ports*) that they define; the database, blob store and HTTP
layer are *adapters* that plug into those ports.

**Why.**
- The service has two unusual infrastructure dependencies (Postgres *and* a blob store) that can fail
  independently. We wanted use cases that read as business flows ("store the blob, then persist the row,
  clean up if that fails") and could be tested without either of them running.
- It makes the failure modes explicit. Each port returns typed errors, so adapters, not use cases, decide
  how an Azure timeout or a JPA constraint violation is translated.

**Cost.**
- More files and more mapping code (entity ↔ domain ↔ DTO) than a plain controller → service → JPA
  repository. For a CRUD service this is a real overhead, and we accepted it because of the two-store
  consistency logic below.

- The boundary is enforced by convention and code review, not by tooling. 

- We are pragmatic about the application layer: use cases carry Spring's `@Service`. 
- A stricter version would wire them in a configuration class.

### 2. Domain / application / adapter split

**Decision.** Three layers, each with one job.

| Layer | Contains | Knows about |
| --- | --- | --- |
| `domain` | `Snippet`, `SnippetVersion`, `SnippetRules` | Nothing else. No Spring, no JPA. |
| `application` | Use cases, ports, `Result`, `SnippetError`, commands and queries | The domain. Never an adapter. |
| `adapter` | Controller, JPA, Azure SDK, scheduler | Application and domain |

**Why.**
- **Domain** holds the rules that are true regardless of how we store or expose snippets: what a valid
  snippet is (`SnippetRules`) and how versions advance (`SnippetVersion.after`, `Snippet.update`). Version
  numbering used to be hand-built inside `UpdateSnippetUseCase`; moving it into the domain means there is
  one place that knows "next version is previous + 1, and it has no formatted content yet".
- **Application** orchestrates: it decides the order of side effects (blob first, then database, then
  compensation). That order *is* the business logic of this service, so it must be readable and unit
  testable, and it can't live in a controller or a repository.
- **Adapters** are the only code allowed to import `jakarta.persistence`, the Azure SDK or Spring MVC. A
  swap (another blob store, another DB) touches one package.

**Cost.** Three representations of "a snippet" exist: the domain `Snippet`, JPA entities, and web DTOs.
The domain is also still fairly thin: most behaviour is validation and versioning, not a rich model.

### 3. Light CQRS

**Decision.** Commands (create, update, delete, cleanup) and queries (get, list, download) are separate
use cases in separate packages (`application/command`, `application/query`), with separate outbound ports:
`SnippetRepository` (writes) and `SnippetQueryRepository` (reads). The read side has its own models
(`SnippetMetadata`, `SnippetListItem`).

**Why it was worth it here.** The read and write paths need *different things from the data*:

- **Update needs metadata but not content.** To bump the version number, `UpdateSnippetUseCase` only needs
  the owner and the current version number. `findMetadataById` returns exactly that and never touches blob
  storage. With a single "load the snippet" method, every update would download the old blob for nothing.
- **List needs neither blobs nor versions.** `findAll` runs a paginated, filtered, sorted query using JPA
  Specifications and returns `SnippetListItem`s straight from the `snippets` table.
- **Get/download need the content**, so only `findById` pays the blob round trip, and it does so without
  holding a database connection 
- **Writes need transactions and compensation**; reads don't. Keeping them apart stops transactional
  concerns leaking into read code.

**Why only "light".** 

- one database, one set of tables, the same JPA entities on both sides;
- no event sourcing, no read replicas, no projections, no async propagation;
- reads are immediately consistent with writes.

Full CQRS buys independent scaling and read models at the cost of eventual consistency and much more
infrastructure. We have no workload that justifies that.

**Cost**
- `SnippetQueryRepository.findById` talks to both Postgres and blob storage, which is a lot for something
  called a repository.
- `findReferencedContentKeys` is a maintenance query used by the cleanup job, and it sits on the read port.
- Several read models (`SnippetMetadata`, `SnippetListItem`, `Snippet`) overlap.

### 4. The Result pattern

**Decision.** Ports and use cases return `Result<SnippetError, T>` (a sealed `Success` / `Failure`) instead
of throwing. `SnippetError` is a sealed hierarchy:

`SnippetNotFound`, `SnippetConflict`, `InvalidInput`, `ContentMissing`, `SnippetCorrupted`,
`ContentStoreUnavailable`, `PersistenceUnavailable`, `UnsupportedLanguage`, `InvalidSnippetContent`,
`LanguageServiceUnavailable`.

**Why.**
- **The commit that introduced it explains the trigger:** use cases were throwing Spring/JPA exceptions or
  returning `null`/`Boolean`, which leaked infrastructure types into the application layer *and* left orphan
  blobs, because cleanup only happened for one specific exception type. Any other DB failure skipped it.
- Failure is part of the function signature. A caller can't forget that `store` or `findById` can fail.
- Exhaustive `when` over a sealed type means **adding a new `SnippetError` is a compile error until
  `SnippetErrorMapper` maps it to an HTTP status**. Nothing falls through to a generic 500 by accident.
- Failure paths are ordinary values, so they are trivial to test: return `Result.Failure(...)` from a mock,
  assert on the outcome.
- Chaining (`flatMap`/`map`) expresses "if this step fails, stop and propagate" without try/catch ladders.

**Cost.**
- `flatMap` chains are more verbose than straight-line code, and the nesting in `ListSnippetsQueryFactory`
  shows it.
- Adapters still catch `RuntimeException` at the boundary and convert it, so unexpected bugs get reported as
  "unavailable" (503) rather than crashing loudly. That is deliberate for infrastructure failures but can
  mislabel a programming error.
- Kotlin has no checked exceptions, so nothing stops a use case from throwing anyway. The discipline is by
  convention.
- The type is hand-rolled (`Result<E, T>`, error first) instead of `kotlin.Result` (which only carries
  `Throwable`) or an external library. It's only around 40 lines and carries a typed error.

### 5. Two stores, no shared transaction

Snippet **metadata** lives in PostgreSQL and snippet **content** lives in Azure Blob Storage (Azurite locally). Every edit creates a new immutable
version.

The central constraint of the service: 
- **a snippet is a Postgres row plus a blob, and no transaction can span both.** 
- Every write has a window where one store has changed and the other hasn't. Several decisions below exist because of this.

**Decision (write path): `storeThenPersist`.**

```
create / update
  1. store the blob under a NEW, immutable key
        snippets/{snippetId}/versions/{versionId}/original
  2. in ONE database transaction: insert the version row + upsert the snippet row
  3. if step 2 fails for ANY reason → best-effort delete of the blob from step 1
```

**Why this order.**
- **Blob first, database second** means the worst failure is an *unreferenced blob* (harmless garbage), never
  a *row pointing at a blob that doesn't exist* (a corrupt snippet that errors on read).
- **Keys are never reused.** The key contains a fresh version id, so a blob is written once and never
  overwritten. A retry or concurrent writer can't clobber content that a committed row depends on. This
  also makes compensation safe: deleting "my" key can never delete someone else's content.
- **Both rows go in one real DB transaction.** `JpaSnippetRepositoryAdapter` uses a `TransactionTemplate`, so
  the version insert and snippet upsert commit or roll back together. We use the template rather than
  `@Transactional` so the exception is caught and mapped to a `Result` *outside* the transaction boundary,
  after rollback has happened.
- **Compensation runs on any failure**, not only constraint violations. 

**Decision (delete path): database first, blobs second.**
- `DeleteSnippetUseCase` deletes rows in a transaction and returns the content keys, *then* deletes blobs
best-effort, logging failures. 

- The row is the source of truth: once it is gone the snippet is gone, and any
blob we fail to delete is an orphan for the cleanup job.

- The opposite order could leave a live snippet with
deleted content.

**Decision (read path): don't hold a DB connection while talking to blob storage.**
- `findById` loads rows, releases the connection, and only then downloads blobs. 
- A slow blob store can't exhaust the connection pool.

### 6. Cleanup instead of an outbox

**The problem.** Compensation (step 3 above) is best-effort. 
- If the process crashes between the blob upload and the DB commit, or the compensating delete itself fails, or a blob delete fails after a snippet delete,
an **orphan blob** remains. 
- Something has to reclaim them eventually.

**What we considered: a transactional outbox.** Record "blob X should be deleted" in the same DB transaction
as the data change, and have a relay process the outbox table and perform the deletion.

**What we chose: a periodic cleanup** (`OrphanedContentCleanupJob` → `DeleteOrphanedContentUseCase`). It lists
blobs, finds the ones no `snippet_versions` row references, and deletes them.

**Why we preferred the cleanup.**
- **It handles every orphan source with one mechanism**: failed creates, failed updates, failed deletes,
  crashes mid-write, even blobs from bugs we haven't found yet. An outbox only covers failures we remembered
  to record, and has nothing to say about a crash *before* the outbox row was written (which is exactly
  the crash window that matters, since the blob is uploaded before the transaction).
- **No extra database writes** on the hot path, no new table, no relay process, and no ordering, retry or
  poison-message logic.
- **It's stateless and idempotent.** Running it twice, or on two instances at once, is harmless. No
  distributed lock is needed.
- **The thing being protected is cheap.** An orphan is wasted storage, not data loss, so eventual cleanup is
  an acceptable guarantee. An outbox is the right tool when you must not lose or duplicate a *message*; here
  we just need garbage collection.

**Safety rules.**
- **Grace period (1 hour).** A blob is uploaded *before* its DB transaction commits, so a just-written blob
  looks unreferenced. The sweep ignores blobs younger than `GRACE_PERIOD`, so it can't delete an in-flight
  upload. The age check lives in the use case (not the Azure adapter) so it's unit-testable without Azurite.
- **Batched reference lookup.** Candidate keys are checked against the database in batches of 500.
- **Failures don't abort the run.** A failed delete is logged and skipped; the next run retries it.
- **Fails safe.** If the reference lookup throws (database down), the sweep aborts and deletes nothing.
- **Runs on a cron** (`snippet.orphaned-content-cleanup.cron`, default daily at 03:00).

**Cost.**
- **Delayed reclamation:** an orphan lives for at least the grace period plus up to one cron interval.
- **O(blobs) work per run.** `listAll()` currently loads all blob keys into memory, which is fine at our scale
  and a known scaling limit 
- **The grace period is a heuristic.** It assumes no DB transaction stays open for an hour. Request timeouts
  make that safe in practice, but it is a timing assumption rather than a proof.
- **Needs careful wiring:** the cleanup's correctness depends on `findReferencedContentKeys` seeing both
  original and formatted keys.

### 7. Blob storage is an adapter, not an asset-service

**The question.** Should content storage be a separate microservice (an "asset service") that exposes the
operations the `SnippetContentStorage` port needs, with this service calling it over the network?

**What we chose:** an in-process **adapter** (`AzureBlobSnippetContentStorage`) behind the
`SnippetContentStorage` port.

**Why.**
- **The interface is tiny and generic**: `store`, `retrieve`, `delete`, and `listAll` (only for cleanup).
  A service that wraps four blob operations adds a network hop, a deployment, auth between services, its
  own retries and its own failure modes, while contributing no domain logic of its own. It would be a
  pass-through.
- **It wouldn't remove the hard problem.** The DB ↔ blob consistency gap in section 5 would still exist,
  just across an HTTP boundary, with *more* ways to fail (timeouts leave you not knowing whether the upload
  happened). We'd need the same compensation and cleanup logic, plus idempotency on the asset service.
- **One owner for one lifecycle.** Snippet content has no life outside snippets: no sharing, no other
  consumers, no independent scaling profile or access policy. A separate service is justified when a
  capability has its own consumers, scale or team. We have none of those today.
- **Operational cost.** Another repo, pipeline, image, health check and failure to monitor, for a
  course-sized system.
- **We aren't locking ourselves in.** Hexagonal architecture is what makes this reversible: the port is the
  contract. Extracting an asset service later means writing a new adapter (an HTTP client implementing
  `SnippetContentStorage`) and changing nothing in the domain or use cases.

**Cost.** 
- The service is coupled to Azure's SDK in one adapter. The SDK client is synchronous, so a slow
blob store occupies a request thread
- we bound that with explicit retry options (3 tries, 10 s per try,
exponential backoff) so a request fails fast instead of hanging.

### 8. Data races

| Race                                                 | What happens | How it is handled                                                                                                                                                                            |
|------------------------------------------------------| --- |----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Two concurrent updates of the same snippet**       | Both read version *N*, both try to write *N+1* | A **unique constraint on `(snippet_id, version_number)`**. The loser gets a constraint violation → `SnippetConflict` → **409**, and its blob is cleaned up. The client re-reads and retries. |
| **Blob written but DB write fails or never happens** | Unreferenced blob | Immediate best-effort delete, then the cleanup as the safety net.                                                                                                                            |
| **cleanup vs. an in-flight write**                   | A blob just uploaded looks unreferenced | The **1-hour grace period.**                                                                                                                                                                 |
| **Two cleanup jobs at once** (multiple instances)    | Both try to delete the same blob | `deleteIfExists` makes deletion idempotent; no lock needed.                                                                                                                                  |
| **Delete vs. read**                                  | Row gone but blob remains briefly, or vice versa | DB is the source of truth. A missing blob is reported as `ContentMissing` (500), a missing row as `SnippetNotFound` (404).                                                                   |

**Why a unique constraint rather than `@Version` (optimistic locking).**
- The invariant we need is "one row per snippet version number", and a database constraint enforces that
  where it can't be bypassed, regardless of which code path or instance writes.
- `@Version` on the snippet entity would not work as-is: the adapter builds a fresh `SnippetEntity` on every
  save, and a null `@Version` makes Spring Data treat each save as an *insert*. Using it properly would mean
  loading and mutating entities, a much larger change.
- The constraint also covers the stale read in `UpdateSnippetUseCase` (metadata is read in one transaction,
  the write happens in another) without changing the flow.

**Cost.** 
- This is *detect and reject*, not *serialise*: concurrent writers get a 409 instead of being queued. 
- That's the right default for edits a human made on stale data, and the client decides what to do.

### 9. Error handling

Errors are values (section 4) and flow outward through three stages:

1. **Adapters translate infrastructure failures into domain errors.**
   - JPA: `DataIntegrityViolationException` → `SnippetConflict`; other `RuntimeException` →
     `PersistenceUnavailable`.
   - Azure: 404 on retrieve → `ContentMissing`; any other SDK failure → `ContentStoreUnavailable`.
   - Permission service: 400 → `InvalidInput`, 403 → `AccessDenied`, 409 → `AlreadyShared` (share) or
     `SnippetConflict` (owner registration); anything else, timeouts included → `PermissionServiceUnavailable`.
   - Raw exception messages are logged, never returned to clients.
2. **Use cases combine and propagate them** with `flatMap`/`map`, and own the compensation logic.
3. **`SnippetErrorMapper` maps each error to an HTTP status** in one place, as RFC 7807 `ProblemDetail`:

| Error | Status | Meaning |
| --- | --- | --- |
| `InvalidInput` | 400 | Bad request data, bad filter, bad parameter |
| `UnsupportedLanguage` | 400 | The language or its version isn't supported |
| `InvalidSnippetContent` | 400 | Content breaks a language rule; `rule`, `message` and `span` (line and column) are top-level fields, see [Invalid code](#invalid-code) |
| `AccessDenied` | 403 | The caller can't perform that action, or the snippet doesn't exist for them |
| `SnippetNotFound` | 404 | No such snippet |
| `SnippetConflict` | 409 | Constraint violation, e.g. a concurrent update |
| `AlreadyShared` | 409 | The snippet is already shared with that user |
| `ContentStoreUnavailable` | 503 | Blob storage failed or timed out |
| `PersistenceUnavailable` | 503 | Database failed |
| `LanguageServiceUnavailable` | 503 | The language service failed or timed out |
| `PermissionServiceUnavailable` | 503 | The permission service failed, timed out or answered unexpectedly |
| `UserDirectoryUnavailable` | 503 | The Auth0 Management API failed, timed out or rejected the M2M token |
| `ContentMissing` | 500 | A row references a blob that doesn't exist (our bug / data loss) |
| `SnippetCorrupted` | 500 | Row points at a missing version (inconsistent data) |

**Design choices.**
- 503 vs. 500 is deliberate: 503 means "transient, retrying may work"; 500 means "our data is wrong".
- Validation happens before any side effect. Invalid create/update requests never touch storage or the
  database, and invalid list filters are rejected before the query runs (section 10).
- Compensation failures are **logged, never returned**: if cleanup of an orphan fails, the user must see the
  *original* error, and the sweep will handle the leftover.

### 10. Filters as a plugin architecture

**Decision.** Each list filter (`name`, `language`, `valid`) is its own Spring `@Component` implementing
`SnippetFilter`. `SnippetFilterRegistry` collects all of them and exposes two operations:

- `validate(filters)`: rejects unknown names and asks each filter to validate its own value;
- `toSpecification(filters)`: ANDs every filter's predicate into one JPA `Specification`.

```kotlin
interface SnippetFilter {
    val parameterName: String
    fun validate(value: String): String? = null   // error message, or null if valid
    fun toPredicate(value: String, root: Root<SnippetEntity>, criteriaBuilder: CriteriaBuilder): Predicate?
}
```

**Why (open/closed).** 
- **Adding a filter is adding one class.** 
- No central `when` statement, no shared file that every new filter edits, no registry change, 
- Filters don't conflict in merges and can't break each other. 
- Each filter owns both its value rules and its predicate, so the knowledge about one query parameter
lives in one place.

**Why validation is part of the plugin.** 
- Validation used to happen lazily *inside* the JPA specification,
so bad input surfaced as a 500 after the query had started. 
- Now the registry implements`SnippetFilterValidator`, a port that `ListSnippetsUseCase` calls *before* querying. Bad input becomes an
`InvalidInput` (400). 
- The use case needs no knowledge of which filters exist, and new filters validate themselves.

**Safety nets.** 
- Duplicate `parameterName`s fail at application startup, not at runtime. 
- A filter name that wasn't validated is a programming error (`checkNotNull`), not a user error.

**Cost.**
- Indirection that is overkill for only three filters. It pays off as filters grow.
- Filter values stay `Map<String, String>` all the way down. We trade type safety for extensibility. (We
  tried a typed `SnippetFilters` data class and dropped it: it brought type safety, but every new filter
  meant editing it and the query factory, the exact central-file problem this design avoids.)
- Filter semantics are only enforced where the filters are registered, so the application layer sees opaque
  strings.

### 11. Validation lives in the domain

`SnippetRules.violation(...)` returns a message (or `null`) rather than throwing. It checks that `name`,
`language` and `languageVersion` aren't blank, that text fields fit their `VARCHAR(255)` columns, and that
content is at most 1,000,000 characters.

- **In the domain, not the controller:** the rules are about what a valid snippet *is*, so they apply no
  matter who calls the use case (HTTP today, anything else tomorrow), and they're unit-testable without
  Spring.
- **Returns a message instead of throwing:** consistent with the `Result` approach; use cases turn it into
  `InvalidInput`.
- **Checked before any side effect**, so a bad request never uploads a blob.
- **Length limits mirror the schema.** Without them a long name would reach the database, fail, and come
  back as a misleading 409.

**Cost.** 
- The 10000-character limit is a chosen default, not a derived requirement. It counts characters, not bytes. 
- Rules are checked by the use cases rather than being unrepresentable in the types.

### 12. Language rules live behind ports

**Decision.** Two outbound ports, both `fun interface`s returning `Result<SnippetError, Unit>`:

| Port | Question it answers | Failure |
| --- | --- | --- |
| `SupportedLanguages.check(language, languageVersion)` | Can snippets use this language and version? | `UnsupportedLanguage`, `LanguageServiceUnavailable` |
| `SnippetContentValidator.validate(language, languageVersion, content)` | Does the content respect the language's rules? | `InvalidSnippetContent(rule, message, span)`, `LanguageServiceUnavailable` |

- Create, update and content-only update call them before touching blob storage or the database. 
- Content-only update has no language in the request, so it validates against the snippet's stored language and version.

**Why.**
- **The assignment requires it.** 
  - User stories #1 to #4 say an invalid snippet must be rejected, with the
    broken rule and its line and column. 
  - The consigna also says PrintScript is only the first language.
- **The knowledge isn't in this service.** 
  - Parsing and language rules will be owned by a separate language microservice that doesn't exist yet. 
  - This service shouldn't embed a parser, and it shouldn't wait for one.
- **A port makes that service easy to plug in.** 
  - The use cases depend on the interface, so wiring in the real service means writing one adapter (an HTTP client). 
  - The use cases and their tests don't change.
- **One strategy per language lives on the other side.** 
  - Picking a parser by language and version is the language service's job. 
  - Language is just data passed through.
- **Same style as the other ports:** 
  - typed errors (including "the service is down", which maps to 503 instead of a misleading 400) 
  - and checks that run before any side effect.

**Today.** `EngineLanguageServiceAdapter` implements both ports against `printscript-engine`, through its
proxy (`ENGINE_URL`):
- `check` is `GET /versions`; the version must be in `versions`. A `400 UnknownLanguage` from the proxy (no
  engine for that `X-Language`) is `UnsupportedLanguage`.
- `validate` is `POST /validate` with `{code, version}`. `valid=false` becomes `InvalidSnippetContent` with the
  engine's `rule`, `message` and `span`. A `400 UnsupportedVersion` is `UnsupportedLanguage`; any other `400` is
  `InvalidInput` with the engine's detail.
- The language travels in the `X-Language` header, exactly as stored; the proxy matches it case-insensitively.
- Timeouts: 2 s to connect, 10 s to read. **502/503/504 are retried once, immediately**: the proxy can still
  answer them for a moment after an engine instance dies, and every engine operation is safe to repeat. Any
  other status, a second failure or an I/O error is `LanguageServiceUnavailable` (503).
- The engine is internal and unauthenticated, so this client has no `BearerTokenForwarder`: users' tokens
  never reach it. The code is never logged.

`EngineInteractiveRunner` implements `InteractiveRunner` (the run socket) on the engine's `/run/interactive`,
with the same rules: `X-Language`, no token, connect timeout 5 s with one retry.

**Cost.**
- Every create and update makes two network calls before saving, so latency and availability depend on the
  engine. The 503 mapping makes that failure explicit.
- Two ports means two calls. They could be merged if the engine ends up exposing a single endpoint.

### 13. Permissions live in another service

Who owns a snippet and who it was shared with belong to `snippet-permission-service`, with its own database.
This service only asks it, through the `SnippetPermissions` port and an HTTP adapter.

- **Check before touching anything.** Every use case authorizes first, so a denied request never reads the
  database or the blob store.
- **403 instead of 404.** The permission is checked before the snippet is loaded, so a user without access
  gets `403` whether or not the snippet exists. Ids of other users' snippets are not revealed.
- **Create registers the owner after saving.** If the registration fails, the snippet and its blob are
  deleted (best effort, logged). Otherwise it would exist with nobody able to access it.
- **Delete removes the permissions last.** The snippet is deleted first; if the permissions can't be
  removed it is only logged. A leftover permission points at a snippet that no longer exists, which is
  harmless: lists only return snippets that exist in this database.
- **Listing asks for ids, filtering stays here.** The permission service returns the ids the user can
  access for the requested relationship, and the query adds `id IN (...)` to the filters, sorting and
  pagination of this service. Our `OWNED` maps to its `OWNER`.
- **Fail fast.** The HTTP client has a 2 s connect and 5 s read timeout; a slow permission service turns into
  a `503` instead of holding request threads.
- **Not exposed publicly.** Only this service talks to the permission service, inside the stack network.
  The UI shares snippets through `POST /snippets/{id}/share`.

### 14. Authentication with Auth0

Users log in with Auth0 (Authorization Code + PKCE in the UI) and every request carries their access token.
Auth0 answers *who* the user is and what the app may do (scopes); `snippet-permission-service` keeps
answering *which snippets* they can touch. It is the "RBAC for the app, ACL for the resources" split from the
course.

- **Validation by configuration.** `issuer-uri` and `audiences` in `application.yml` make Spring check the
  signature (keys downloaded from the tenant on the first request), `iss`, `aud` and `exp`. There is no
  custom decoder to get wrong.
- **Scopes per HTTP method.** Reads need `read:snippets`, writes `write:snippets`, in one rule in
  `SecurityConfiguration` instead of an annotation per endpoint.
- **The use cases don't know about tokens.** Controllers receive the user through `@CurrentUserId`, resolved
  from the token's `sub`. Replacing the old `X-User-Id` header changed no use case.
- **The token travels to the permission service.** `BearerTokenForwarder` adds the user's token to every call,
  and the permission service validates it again and takes the user from it. A service in the stack network
  can't act for a user without that user's token.
- **User search with a machine-to-machine token.** #7 needs every user, which only the Management API knows.
  Its client secret can't live in the browser, so the search is `GET /users` here. The M2M token is cached
  until a minute before it expires, and forgotten if Auth0 rejects it. Only ids and names are returned:
  emails are used to match, never shown to other users.
- **The query can't be injected.** The user's text is escaped before it goes into Auth0's Lucene query.
  Shorter than 3 characters it is matched as a prefix, because Auth0 refuses leading wildcards on shorter
  terms.

### 15. Monitoring with New Relic

Each microservice runs as an independent application, with its own JVM, lifecycle and failure points. For this reason, the New Relic Java Agent is configured separately in each service rather than relying on a single monitoring agent for the entire stack.

The Java Agent instruments the application at runtime without requiring changes to the application code. It collects telemetry such as:

- **Application performance:** request latency, throughput and transaction metrics.
- **Errors:** exceptions and failed transactions.
- **Distributed tracing:** follows requests across service boundaries, helping identify where latency or failures originate.

Having the agent in every microservice provides visibility into each service independently while allowing us to correlate activity across the system. This is particularly useful when troubleshooting failures involving communication between services.

The agent is downloaded during the Docker image build and attached to the JVM when the container starts. Each service reports its telemetry to the same New Relic account, providing a centralized view of the application.

### 16. Smaller decisions

- **Immutable versions.** Editing never modifies a version; it creates a new one with a new blob. This is
  what makes blob keys safe and history trustworthy, at the price of storage growth (there is no version
  pruning).
- **Constructor-injected `Clock`.** Time comes from a bean, so use cases are deterministic in tests.
- **No `open-in-view`.** Entities don't leak into the web layer and connections aren't held across a request.
- **Entities are not domain objects.** JPA entities are plain Kotlin classes in the adapter, with ids and
  keys but no behaviour. Mapping is explicit.
- **Stable sort.** List results always add `id` as a final sort key so pagination is deterministic.
- **Case-insensitive matching** for owner, language and name filters; `LIKE` wildcards in user input are
  escaped.
- **Convention plugins** (ktlint, detekt, Spring and JPA setup) come from a shared Gradle plugin repository,
  so style and static analysis are enforced the same way across the team's services. `./gradlew check`
  fails on violations.

---

## Known limitations

We would rather list these than have them discovered:

- **Scheduled work has no user token.** The orphan cleanup doesn't call the permission service, so it is not
  affected, but any future background job that needs it will need its own machine-to-machine token.
- **The author is shown as an Auth0 id.** Snippets store the owner's `sub` (`auth0|...`); showing the
  owner's name would need a lookup in the user directory when listing.
- **No unshare endpoint.** The permission service supports revoking a share, but this service doesn't expose
  it yet.
- **Large `IN` lists.** Listing sends every accessible id in one query; fine for the expected volume, not for
  users with tens of thousands of snippets.
- **Formatting isn't implemented.** `formattedContent` is never written, and
  `download?version=formatted` currently returns the *original* content.
- **`valid` is not persisted yet.** Every snippet reports `valid = true`, `valid=false` returns nothing and
  `sortBy=valid` sorts by name. That is still correct today: create and update reject invalid code, so every
  stored version passed the engine's `/validate`. A real flag only matters once snippets are re-validated
  (a new parser version, the lint pipeline over Redis Streams), and it will be added with that pipeline.
- **Runs use the saved version.** The run socket loads the snippet at handshake time and never accepts code
  from the client, so unsaved changes in the editor are not what runs.
- **No foreign keys or migrations.** The schema comes from `ddl-auto: update`; there are no FKs between
  `snippets.current_version_id`, `snippet_versions.snippet_id` and the tables. The unique version constraint
  is created that way too, and adding it to a database that already has duplicate versions will fail.
- **Delete vs. update race.** An update that lands after a concurrent delete can leave a version row for a
  snippet that no longer exists. Because that row references its blob, the sweep never removes either. A FK
  would fix this.
- **Cleanup scales linearly.** `listAll()` holds every blob key in memory; the sweep is fine for thousands
  of blobs, not for millions.
- **Startup depends on Azure.** The container is created in the storage adapter's init block, so the app
  fails to start if blob storage is unreachable.

## Testing

- **Domain:** `Snippet`, `SnippetVersion`, `SnippetRules` tested as plain unit tests.
- **Application:** every use case is tested with Mockito-mocked ports and an in-memory
  `FakeSnippetPermissions`, which is why the ports exist. Each one checks that a denied request touches
  nothing:
  success, not-found, storage failure, DB failure with blob cleanup, conflict, and invalid input (asserting
  nothing downstream is called).
- **Adapters:** controllers, error mapper, query factory and the filter registry are covered in isolation.
  The permission adapter is tested against `MockRestServiceServer` (requests sent, the forwarded token, and
  every answer mapped to its error), and so are the Auth0 user search, the M2M token cache and the engine
  adapter (versions, validation with and without a span, retry once on 502/503/504, no token sent).
- **Invalid code:** `InvalidSnippetContentResponseTest` (`@WebMvcTest`) checks the exact `400` JSON, with
  `rule`, `message` and `span` at the top level.
- **Run socket:** `SnippetRunWebSocketHandlerTest` drives the handler with a fake session (error frames and
  close codes, start/input, rejections, relayed events); `RunWebSocketIntegrationTest` runs a real server with
  a fake engine socket: the token in `Sec-WebSocket-Protocol`, `401` before the upgrade, `403`/4403 without
  READ, and a whole run relayed through `EngineInteractiveRunner`.
- **Security:** `SecurityConfigurationTest` (`@WebMvcTest` with mock JWTs) checks `401` without a token,
  `403` without the scope, and that the caller is the token's `sub`.
  `embedded-azurite` is available on the test classpath for running the Azure adapter against a real
  emulator.
- **Quality gates:** `./gradlew check` runs the tests plus ktlint and detekt; CI runs it on every pull
  request.

