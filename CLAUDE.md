# CLAUDE.md

Ktor 3 + Kotlin 2 + Gradle KTS service. Pedagogical: each `tour/*` route is intentionally short and focused on one Kotlin language feature. Don't refactor away the duplication between tour routes — the duplication is the point.

## Commands

```bash
./gradlew run            # localhost:8080
./gradlew test           # all tests
./gradlew installDist    # build/install/kotlin-tutorial/bin/kotlin-tutorial
```

## Layout

- `src/main/kotlin/dev/michaellamb/tutorial/Application.kt` — `EngineMain.main`, `Application.module()`
- `plugins/` — `Serialization` (Jackson), `Monitoring` (CallLogging), `Cors` (allows blog origins), `Routing` (mounts everything), `OpenApi` (post-processes the generated spec)
- `/swagger` — Swagger UI with fully testable endpoints. The OpenAPI spec is generated from the live routing tree by the `ktor { openApi { codeInferenceEnabled = true } }` compiler plugin (build.gradle.kts), which infers schemas/params from each handler's `call.receive`/`respond`/`parameters` — so tour routes stay untouched. Mounted in `Routing.kt` via `swaggerUI("/swagger") { source = OpenApiDocSource.Routing(...) }`.
- `tour/` — one file per language feature; **add new tour routes here, mount in `Routing.kt`**
- `widgets/` — one file per server-rendered HTML widget consumed by the blog's `/now.html`, `/cluster.html`, and `/about.html`. Each demonstrates one Kotlin feature (kotlinx.html DSL, Jackson + data classes, structured concurrency, java.time time-window filtering). 60s in-memory TTL cache via `WidgetCache`. `RecentlyUpdatedWidget.kt` also holds `NowStore`, the client for the curated "Recently updated" feed (Cloudflare Worker + KV at `now-store.michaellamb.dev`). `NowDigest.kt` exposes `GET /widgets/now.json` — a public JSON aggregation of every /now section (Recently updated + Letterboxd + Steam + GitHub), fetched concurrently with `coroutineScope { async }` and degrading per-section to empty on failure, 60s-cached. It's consumed by the `homelab-bot` `/now` Discord command; each widget exposes an `internal suspend fun fetchX(client)` (e.g. `fetchFilms`, `fetchRecentGames`, `fetchRepoGroups`) shared by its HTML route and the digest. `ProjectsWidget.kt` (`/widgets/projects`) renders the curated Projects list for the blog's `/about` page — its data source is this service's **own** SQLite store (`ProjectRepository`), not an upstream API, so the read path is a suspended Exposed transaction.
- `admin/` — `AdminRoutes.kt` is the `/admin` form to publish/delete "Recently updated" entries (proxies writes to the now-store Worker with the Access service token); on a confirmed create it fire-and-forgets the saved entry via `NoteHook.announce` to homelab-bot's LAN webhook (`HOMELAB_BOT_NOTE_HOOK_URL` + `NOTE_HOOK_SECRET`; no-op when unset) so the bot posts it to Discord #general, and on a confirmed delete it fire-and-forgets `NoteHook.retract` (a `DELETE` to the same URL + `/{id}`) so the bot retracts the embed. `NowStore.create` returns the persisted `NowEntry` (parsed from the Worker's 201) so the push only fires on real persistence; the delete push only fires after `NowStore.delete` succeeds. This only syncs explicit admin-UI deletes — a note that disappears via TTL expiry leaves its Discord embed in place (a deliberate scope cut, not an oversight). `ProjectsAdminRoutes.kt` is the `/admin/projects` form to create/edit/delete/reorder (▲/▼) the Projects records, writing straight to the local `ProjectRepository`. Both gated at the edge by **Cloudflare Access** (One-time-PIN email policy over `/admin*`); open locally for dev. JS-free same-origin HTML form POSTs; editing reloads with `?edit=<id>` to pre-fill the form.
- `notes/` — CRUD persisted to SQLite-on-disk via **Exposed (DSL)**. `NotesTable` is the Exposed table; `NotesDatabase.connect()` opens the DB (WAL + busy_timeout set on the `SQLiteDataSource`) and creates the schema; `NoteRepository` runs each op in `newSuspendedTransaction(Dispatchers.IO)`. `createdAt` is stored as **epoch millis (a `long` column)**, not `exposed-java-time`'s `timestamp()` — that column type shifts the `Instant` by the local UTC offset on SQLite round-trips; epoch millis is TZ-proof and sorts chronologically. DB path resolves: system property `notes.db.path` → env `NOTES_DB_PATH` → default `build/notes.db` (gitignored). The container sets `NOTES_DB_PATH=/app/data/notes.db`, mounted as the `kotlin-tutorial-data` named volume on node5 (cluster-ops `group_vars/all/main.yml`) so notes survive redeploys. The `Note` data class + its Jackson/OpenAPI serialization are untouched — Exposed maps rows by hand.
- `projects/` — the curated `/about` Projects list, persisted to its **own** SQLite file (`projects.db`) on the same volume, mirroring the `notes/` pattern exactly: `ProjectsTable` (Exposed), `ProjectsDatabase.connect()`, `ProjectRepository` (suspended-transaction CRUD + `move(id, up)` reorder by swapping `position` values + a blocking `seedDefaults()` that inserts the current projects on first run). `tech` is a comma-separated `text` column ↔ `List<String>`; `position` orders the page; `archived` hides an entry from the widget but keeps it in admin. Plain Jackson data class (no `@Serializable` — no JSON REST surface; the widget renders HTML and admin posts forms). DB path resolves: system property `projects.db.path` → env `PROJECTS_DB_PATH` → default `build/projects.db`; the container sets `PROJECTS_DB_PATH=/app/data/projects.db`.
- `health/HealthRoutes.kt` — `/health` for the Docker `HEALTHCHECK`
- `src/main/resources/application.yaml` — Ktor port/host config
- `src/main/resources/logback.xml` — logging

## Required environment (widgets)

`STEAM_API_KEY`, `STEAM_ID`, `UPTIME_KUMA_STATUS_SLUG` — when unset, those widgets respond with a non-fatal "not configured" fragment so the rest of the app keeps working. `LETTERBOXD_USERNAME` defaults to `michaellamb`. `UPTIME_KUMA_BASE_URL` defaults to `https://status.michaellamb.dev`. `GITHUB_USERNAME` defaults to `michaellambgelo`; `GITHUB_TOKEN` is optional and, when set, raises the GitHub API rate limit from 60/hr to 5000/hr.

**Recently-updated feed:** `NOW_STORE_URL` defaults to `https://now-store.michaellamb.dev`; `CF_ACCESS_CLIENT_ID` / `CF_ACCESS_CLIENT_SECRET` are the Cloudflare Access service-token credentials Ktor presents to the (Access-gated) now-store Worker. When unreachable, `/widgets/recently-updated` degrades to an "Updates unavailable" fragment. These are wired into the container in `cluster-ops` (`group_vars/all/main.yml`, sourced from `~/.zshrc`).

**Projects feed:** no required env — `/widgets/projects` reads the local `projects.db`, and the optional per-project status badge reuses `UPTIME_KUMA_BASE_URL`. `PROJECTS_DB_PATH` (set in the Dockerfile) overrides the DB location; the data rides on the existing `kotlin-tutorial-data` volume, so no cluster-ops change was needed.

## Deploy

Image: `ghcr.io/michaellambgelo/kotlin-tutorial:latest` (**arm64-only**, built natively on **node0**, the M4 Mac mini control node).
Host: `node5`. Public URL: `https://kotlin-tutorial.michaellamb.dev`.
Playbook: `~/Workspace/cluster-ops/playbooks/update-kotlin-tutorial.yml`.

Run **`/deploy`** from this repo (wraps `scripts/deploy.sh`): pushes `main`, then SSHes node0 to run `cluster-ops/scripts/node0-build-deploy.sh kotlin-tutorial` — native arm64 build → push GHCR (`:latest` + `:sha-<short>`) → the playbook pulls on node5, tags the previous image `:rollback`, restarts, and health-gates.

GitHub Actions (`.github/workflows/build-and-push.yml`) still runs **tests on push**, but its image-build job is **`workflow_dispatch`-only** — a manual multi-arch (QEMU) fallback, e.g. if an amd64 image is ever needed.

## Conventions

- Every tour route file has a header comment explaining the language feature it demonstrates.
- Routes are mounted via `Route.xRoutes()` extension functions, not classes — idiomatic Ktor.
- Persistence (the `/notes` endpoints) uses **Exposed (JetBrains) DSL** over SQLite-on-disk — see `notes/`. No HikariCP: SQLite is single-writer regardless of pool size, so a pool buys nothing here. DSL (not DAO) keeps row↔`Note` mapping explicit. If another feature ever needs storage, prefer Exposed over JPA — pick the choice that teaches more Kotlin.
- OpenAPI: Ktor's `openApi` compiler plugin requires **Ktor 3.5+ and Kotlin 2.2.20+** (the version floor was raised for this). Its schema generator is kotlinx.serialization-based, but runtime JSON I/O stays on **Jackson** — DTOs carry `@Serializable` (e.g. `Note`, `Shape`, `HealthResponse`) **only** so the generator can describe them; `serialization/Serializers.kt` maps `UUID`/`Instant` to string schemas. Tour handlers returning ad-hoc `Map<String, Any>` can't be schema'd (kotlinx has no `Any` serializer); `plugins/OpenApi.kt` strips the resulting "Failed to resolve schema" noise so they render as plain objects.
