# Real-Time Gaming Leaderboard

A horizontally scalable, real-time multiplayer leaderboard. Java 21 + Spring Boot, sharded Redis cluster as the hot path, Postgres for player profiles, two app replicas behind an nginx load balancer, JWT-protected REST + STOMP/WebSocket push, Prometheus + Grafana for observability, and a Java load-test client gated behind a Docker Compose `test` profile.

The leaderboard is a Redis sorted set, sharded across **N fixed partitions** (default `4`). Top-K, exact rank, and ±4 neighbors are computed by fanning out across partitions and merging in the app.

## Architecture at a Glance

```
client ──HTTP/WS──> nginx ──> [app-1, app-2] ──ZSETs/PubSub──> Redis Cluster (3M + 3R)
                                  └──> PostgreSQL
                                  └──> /actuator/prometheus ──> Prometheus ──> Grafana
```

Full diagram: `docs/architecture.drawio` (open with [diagrams.net](https://app.diagrams.net) or the VS Code drawio extension).

## What's in the Box

| Path | What |
|---|---|
| `leaderboard-app/` | Spring Boot service — REST + STOMP, Redis cluster client, JPA |
| `load-test-client/` | Spring Boot CLI — virtual-thread driven load generator with Prometheus metrics |
| `infra/nginx/` | Load balancer config (RR for HTTP, `ip_hash` sticky for `/ws`) |
| `infra/prometheus/` | Scrape config |
| `infra/grafana/` | Provisioned data source + four dashboards |
| `infra/postgres/` | Bootstrap |
| `docs/architecture.drawio` | Component diagram |
| `docker-compose.yml` | Single command to bring up the whole stack |

## Quick Start

Build images and start everything (apps, postgres, redis cluster, prometheus, grafana, exporters):

```bash
docker compose up --build -d
```

Wait until both apps are healthy:

```bash
docker compose ps
docker compose logs -f app-1
```

Smoke test:

```bash
# 1. Register a player (returns a JWT)
TOKEN=$(curl -sS -X POST http://localhost/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"secret"}' | jq -r .token)

# 2. Submit a score
curl -sS -X POST http://localhost/api/scores \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"score": 12345}' -i

# 3. Top 10
curl -sS http://localhost/api/leaderboard/top -H "Authorization: Bearer $TOKEN" | jq

# 4. Your rank
curl -sS http://localhost/api/leaderboard/me -H "Authorization: Bearer $TOKEN" | jq

# 5. ±4 neighbors
curl -sS http://localhost/api/leaderboard/me/neighbors -H "Authorization: Bearer $TOKEN" | jq
```

Open Grafana at <http://localhost:3001> (anonymous viewer is enabled; `admin/admin` for editing). The dashboards `Leaderboard App`, `Redis Cluster`, `Postgres`, and `Load Test Client` are pre-provisioned.

Prometheus is at <http://localhost:9091>.

## Run the Load Test

```bash
docker compose --profile test up --build load-client
```

This boots a Spring Boot load client that:

1. Registers/logs in `NUM_PLAYERS` simulated players (cached JWTs).
2. Spawns one virtual thread per player; each loop submits a random score and reads `top`, `me`, and `me/neighbors`.
3. Subscribes `WS_SUBSCRIBERS` of them to the STOMP topics (`/topic/leaderboard/top10` and `/user/queue/rank`).
4. Runs for `DURATION_SECONDS`, then prints a summary.
5. Holds for `HOLD_AFTER_SECONDS` so Prometheus captures final samples.

Tune via env vars in `docker-compose.yml` (or `-e` overrides). While the client is running the **Load Test Client** dashboard in Grafana shows score / read latency, WS frame rate, and active connections; the **Leaderboard App** dashboard shows server-side RPS, p99 latency, scores submitted/sec, top-10 broadcasts/sec, and per-instance JVM heap.

## Run the Unit Tests

```bash
./gradlew :leaderboard-app:test
```

(All 37 tests should pass.) The most interesting ones live in `leaderboard-app/src/test/java/.../leaderboard/LeaderboardServiceTest.java` — they exercise the partitioned rank algorithm against a 4-partition mock with cross-partition tie-break scenarios.

## Endpoints

### REST (all behind JWT except `/api/auth/*`)

| Method | Path | Body / Auth | Notes |
|---|---|---|---|
| POST | `/api/auth/register` | `{username,password}` | Creates a player, returns a JWT |
| POST | `/api/auth/login` | `{username,password}` | Returns a JWT |
| POST | `/api/scores` | `{score}` (Bearer) | Submits a score for the authenticated player |
| GET  | `/api/leaderboard/top` | (Bearer) | Top 10 entries |
| GET  | `/api/leaderboard/me` | (Bearer) | Authenticated player's exact rank |
| GET  | `/api/leaderboard/me/neighbors` | (Bearer) | 4 above + me + 4 below |
| GET  | `/api/players/me` | (Bearer) | Profile + last_score from Postgres |
| GET  | `/actuator/health` | — | |
| GET  | `/actuator/prometheus` | — | Scraped by Prometheus |

### STOMP / WebSocket

Endpoint: `ws://<host>/ws` (no SockJS). The client must send a STOMP `CONNECT` frame with `Authorization: Bearer <jwt>` — validated by `StompAuthInterceptor` against the same `JwtDecoder` used for HTTP.

| Destination | Direction | Payload |
|---|---|---|
| `/topic/leaderboard/top10` | server → all | `LeaderboardResponse` — broadcast every `LEADERBOARD_BROADCAST_INTERVAL_MS` (default 1s) |
| `/user/queue/rank` | server → user | `PlayerRankResponse` — pushed when this player's score changes (regardless of which app instance handled the score write — fan-out is via Redis Pub/Sub on `lb.events`) |

## Sharding Algorithm

Leaderboard is split into `LEADERBOARD_PARTITIONS` (default `4`) Redis sorted sets:

- Key: `{lb:p:N}:scores` — the curly braces are a Redis Cluster hash tag, pinning each partition's ZSET to a single slot. Different partitions land on different slots / masters, distributing write load.
- Player partition: `Math.floorMod(playerId.hashCode(), N)`.

| Query | Algorithm | Redis cost |
|---|---|---|
| Submit score | `ZADD {lb:p:partition}:scores score playerId`; publish to `lb.events` | 1 round trip |
| Top K | Fan out `ZREVRANGE 0 K-1 WITHSCORES` to all N partitions, merge by `(score desc, playerId asc)`, take K | N round trips |
| Player rank | `ZSCORE` user partition; for each partition: own → `ZREVRANK`; others → `ZCOUNT (score, +inf)` + lex-tied count. Sum + 1. | N round trips |
| Neighbors (±R) | Compute global rank → for each partition pull `R+5` entries above and below user score → merge → slice ±R around user | N round trips |

Tie-break across partitions uses `playerId` ascending; that's the single global ordering rule asserted in the unit tests.

## Configuration Reference

`leaderboard-app` (env vars consumed by `application.yml`):

| Var | Default | Notes |
|---|---|---|
| `APP_INSTANCE` | `local` | Tags Micrometer metrics |
| `POSTGRES_HOST` / `_PORT` / `_DB` / `_USER` / `_PASSWORD` | `localhost / 5432 / leaderboard / leaderboard / leaderboard` | |
| `REDIS_NODES` | `localhost:6379` | Comma-separated cluster seed list |
| `JWT_SECRET` | dev placeholder | Must be ≥ 32 bytes (HS256) |
| `JWT_TTL` | `PT1H` | ISO-8601 duration |
| `LEADERBOARD_PARTITIONS` | `4` | N |
| `LEADERBOARD_BROADCAST_INTERVAL_MS` | `1000` | Top-10 push cadence |

`load-test-client`:

| Var | Default | Notes |
|---|---|---|
| `TARGET_BASE_URL` | `http://localhost` | Where the apps are |
| `TARGET_WS_URL` | `ws://localhost/ws` | |
| `NUM_PLAYERS` | `200` | |
| `RAMP_UP_SECONDS` | `10` | Stagger player startup |
| `DURATION_SECONDS` | `60` | Length of the load phase |
| `HOLD_AFTER_SECONDS` | `30` | Keep process alive so Prometheus catches the final scrape |
| `CYCLE_INTERVAL_MS` | `1000` | One score+3-reads cycle per player every N ms |
| `WS_SUBSCRIBERS` | `25` | How many players also open a WebSocket |
| `EXIT_AFTER_RUN` | `true` | Container exits after summary |

## What's Intentionally Simple

- **Single nginx**: in production, run two behind a VIP / anycast for HA at the LB tier.
- **Single Postgres**: in production, use a managed HA Postgres (RDS Multi-AZ, Patroni, etc.). The leaderboard hot path stays available even if Postgres is down — score writes use Redis as the source of truth and the JPA write is best-effort.
- **HS256 JWTs from a static env-var secret**: rotate via JWKS in production.
- **Sharding fan-out is sequential** (`N` synchronous Redis calls per request). With `N=4` and sub-ms Redis latency this is fine for a demo; production code should pipeline / async via Lettuce's `RedisAsyncCommands`.

## Layout

```
real-time-gaming-leaderboard/
├── README.md
├── docker-compose.yml
├── settings.gradle / build.gradle / gradle.properties / gradlew
├── leaderboard-app/
│   ├── build.gradle
│   ├── Dockerfile
│   └── src/{main,test}/...
├── load-test-client/
│   ├── build.gradle
│   ├── Dockerfile
│   └── src/main/...
├── infra/{nginx, prometheus, grafana, postgres}/
└── docs/architecture.drawio
```
