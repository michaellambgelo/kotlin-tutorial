# kotlin-tutorial

[![Status](https://status.michaellamb.dev/api/badge/15/status)](https://status.michaellamb.dev)
[![Uptime (24h)](https://status.michaellamb.dev/api/badge/15/uptime/24)](https://status.michaellamb.dev)
[![Ping](https://status.michaellamb.dev/api/badge/15/ping)](https://status.michaellamb.dev)

Ktor service whose endpoints are written to teach Kotlin language features. Plus a small in-memory CRUD module for `/notes`.

An interactive homepage at `/` lists every route with live "Run ▶" buttons, `/swagger` exposes a fully testable Swagger UI, and `/widgets/*` serves server-rendered HTML fragments for the blog dashboard.

Deployed to `node5` of the homelab Pi cluster, reachable at `https://kotlin-tutorial.michaellamb.dev`.

## Endpoints

### Homepage & API docs

| Endpoint | What |
|---|---|
| `GET /` | interactive HTML directory of every route with live "Run ▶" buttons (kotlinx.html DSL) |
| `GET /swagger` | Swagger UI; the OpenAPI spec is inferred from the routing tree at compile time by Ktor's `openApi` plugin, then post-processed in `plugins/OpenApi.kt` |

### Language tour (`/tour/*`)

| Endpoint | Kotlin idea |
|---|---|
| `GET /tour/data-class` | `data class`, `copy()`, `componentN()`, destructuring |
| `POST /tour/null-safety` | `?.`, `?:`, `let`, smart casts. Body `{"nickname": String?}` |
| `POST /tour/sealed-when` | `sealed interface` + exhaustive `when`. Body `{"type":"Circle","radius":3}` (or `Square`/`Triangle`) |
| `GET /tour/coroutines` | `suspend` + `coroutineScope { async { ... } }` parallel-vs-sequential timing |
| `GET /tour/extensions` | extension functions. Query: `?text=Hello&n=17` |
| `GET /tour/scope-functions` | `let` / `run` / `with` / `apply` / `also` side-by-side |
| `GET /tour/collections` | `groupBy` / `sumOf` / `partition` / `runningFold` |
| `GET /tour/generics` | bounded type params `<T : Comparable<T>>`, `out` variance, `reified` |
| `GET /tour/higher-order-functions` | functions as values, `::` refs, composition. Query: `?x=10` |
| `GET /tour/interfaces` | interface default methods + polymorphic dispatch |
| `GET /tour/sequences` | lazy vs eager evaluation, short-circuit, `generateSequence` |
| `GET /tour/result` | `runCatching` / `Result` / `fold` / `getOrElse`. Query: `?n=16` |
| `GET /tour/reflection` | `KClass`, `memberProperties`, callable references |

### CRUD (`/notes`)

In-memory; restart wipes data.

| Method | Path | |
|---|---|---|
| GET | `/notes` | list |
| GET | `/notes/{id}` | fetch one |
| POST | `/notes` | create — body `{"title":"...","body":"..."}` |
| PUT | `/notes/{id}` | partial update — body `{"title":"...","body":null}` |
| DELETE | `/notes/{id}` | delete |

### Widgets (`/widgets/*`)

Server-rendered HTML fragments consumed by the blog's `/now.html` and `/cluster.html`. Each demonstrates one Kotlin feature; all sit behind a 60s in-memory TTL cache.

| Endpoint | Renders | Kotlin idea |
|---|---|---|
| `GET /widgets/letterboxd` | last 4 films from Letterboxd RSS | kotlinx.html, XML parsing, regex |
| `GET /widgets/steam` | last 5 recently-played games | data-class Jackson DTOs |
| `GET /widgets/cluster` | Uptime Kuma service status | structured concurrency (parallel `async`) |
| `GET /widgets/github` | commits in last 24h, grouped by repo | `java.time` windows, `groupBy` |
| `GET /widgets/recently-updated` | curated feed from the now-store Worker | object as HTTP service client |

Unconfigured widgets degrade to a non-fatal "not configured" / "unavailable" fragment, so the rest of the app keeps working.

### Admin (`/admin`)

Publish/delete entries for the "Recently updated" feed. Gated at the edge by **Cloudflare Access** (email one-time-PIN); open locally for dev. Writes are proxied to the now-store Worker with the Access service token.

| Method | Path | |
|---|---|---|
| GET | `/admin` | form listing current entries |
| POST | `/admin/entries` | publish — form fields `body`, `url?`, `expiry` (`today`/`day`/`week`) |
| POST | `/admin/entries/{id}/delete` | delete an entry |

### Health

`GET /health` → `{"status":"ok","uptimeSeconds":N,"version":"..."}`

## Environment

All optional — widgets and admin degrade gracefully when unset.

| Variable | Used by | Default |
|---|---|---|
| `LETTERBOXD_USERNAME` | letterboxd widget | `michaellamb` |
| `STEAM_API_KEY`, `STEAM_ID` | steam widget | — (widget shows "not configured") |
| `UPTIME_KUMA_BASE_URL` | cluster widget | `https://status.michaellamb.dev` |
| `UPTIME_KUMA_STATUS_SLUG` | cluster widget | — (widget shows "not configured") |
| `GITHUB_USERNAME` | github widget | `michaellambgelo` |
| `GITHUB_TOKEN` | github widget | — (optional; raises API rate limit) |
| `NOW_STORE_URL` | recently-updated widget, admin | `https://now-store.michaellamb.dev` |
| `CF_ACCESS_CLIENT_ID`, `CF_ACCESS_CLIENT_SECRET` | recently-updated widget, admin | — (Cloudflare Access service token) |

## Local development

```bash
./gradlew run            # start at localhost:8080
./gradlew test           # run all tests
./gradlew installDist    # produce build/install/kotlin-tutorial/
```
