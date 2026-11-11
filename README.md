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
  - [12. Smaller decisions](#12-smaller-decisions)
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

# run everything (service, Postgres, Azurite)
docker compose up --build
```

`docker compose` reads these variables (for example from a `.env` file):

| Variable | Purpose |
| --- | --- |
| `SNIPPET_SERVICE_PORT` | Host port for the service |
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | Database credentials |
| `AZURE_STORAGE_CONNECTION_STRING` | Blob storage connection string (Azurite or real Azure) |
| `AZURITE_PORT` | Host port for Azurite's blob endpoint |
| `USERNAME`, `TOKEN` | GitHub Packages credentials, used as Docker build secrets |

Optional property: `snippet.orphaned-content-cleanup.cron` (default `0 0 3 * * *`, daily at 03:00).

## API

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

- The three `/upload` endpoints exist because the assignment's user stories #1 and #2 require creating and
updating snippets through file uploads. 
- They reuse the same use cases as the JSON endpoints, so the same
validation and versioning apply. 
- The file must be non-empty UTF-8 text, otherwise the response is `400`.

## Architecture

```
com.ingsisbry.snippet
├── domain/                     Snippet, SnippetVersion, SnippetRules   (pure Kotlin)
├── application/
│   ├── command/                create, update, delete, cleanup         (write side use cases)
│   ├── query/                  get, list, download                     (read side use cases)
│   ├── port/out/               SnippetRepository, SnippetQueryRepository,
│   │                           SnippetContentStorage, SnippetFilterValidator
│   ├── error/                  sealed SnippetError
│   └── common/                 Result, PageResult
└── adapter/
    ├── web/                    controller, DTOs, error → HTTP mapping
    ├── persistence/            JPA entities, repositories, filters, specifications
    ├── storage/azure/          blob storage adapter
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
`ContentStoreUnavailable`, `PersistenceUnavailable`.

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
   - Raw exception messages are logged, never returned to clients.
2. **Use cases combine and propagate them** with `flatMap`/`map`, and own the compensation logic.
3. **`SnippetErrorMapper` maps each error to an HTTP status** in one place, as RFC 7807 `ProblemDetail`:

| Error | Status | Meaning |
| --- | --- | --- |
| `InvalidInput` | 400 | Bad request data, bad filter, bad parameter |
| `SnippetNotFound` | 404 | No such snippet |
| `SnippetConflict` | 409 | Constraint violation, e.g. a concurrent update |
| `ContentStoreUnavailable` | 503 | Blob storage failed or timed out |
| `PersistenceUnavailable` | 503 | Database failed |
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

### 12. Smaller decisions

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

- **No authentication or authorization.** The owner is hard-coded (`Anonymous` on create, `anonymous` on
  list); anyone with a snippet id can read, change or delete it. `relationship=OWNED` behaves like `ALL`,
  and `SHARED` always returns an empty page.
- **Formatting isn't implemented.** `formattedContent` is never written, and
  `download?version=formatted` currently returns the *original* content.
- **`valid` is a stub.** Every snippet reports `valid = true`, `valid=false` returns nothing and
  `sortBy=valid` sorts by name.
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
- **Application:** every use case is tested with Mockito-mocked ports, which is why the ports exist:
  success, not-found, storage failure, DB failure with blob cleanup, conflict, and invalid input (asserting
  nothing downstream is called).
- **Adapters:** controller, error mapper, query factory and the filter registry are covered in isolation.
  `embedded-azurite` is available on the test classpath for running the Azure adapter against a real
  emulator.
- **Quality gates:** `./gradlew check` runs the tests plus ktlint and detekt; CI runs it on every pull
  request.

