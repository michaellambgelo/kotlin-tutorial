# kotlin-tutorial

Ktor service whose endpoints are written to teach Kotlin language features. Plus a small in-memory CRUD module for `/notes`.

Deployed to `node5` of the homelab Pi cluster, reachable at `https://kotlin-tutorial.michaellamb.dev`.

## Endpoints

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

### Health

`GET /health` → `{"status":"ok","uptimeSeconds":N,"version":"..."}`

## Local development

```bash
./gradlew run            # start at localhost:8080
./gradlew test           # run all tests
./gradlew installDist    # produce build/install/kotlin-tutorial/
```

## Build & deploy

Push to `main` → GitHub Actions builds a multi-arch image (`linux/amd64,linux/arm64`) and pushes to `ghcr.io/michaellambgelo/kotlin-tutorial:latest`.

Deploy to node5:

```bash
cd ~/Workspace/cluster-ops
ansible-playbook playbooks/update-kotlin-tutorial.yml
```

The playbook captures a `:rollback` tag, pulls the new image, swaps the container, and waits for the in-container `HEALTHCHECK` to report healthy.
