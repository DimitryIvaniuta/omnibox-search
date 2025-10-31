# omnibox-search

A stateless Spring Boot (Java 21) GraphQL service that powers the **omni‑box** search across multiple entity types (Contacts, Listings, Referrals, Transactions, Products, Mailings). It queries a **read‑optimized Postgres index** (FTS + trigram), returns **grouped and ranked** results, and is designed for **debounced GraphQL‑over‑HTTP** queries.

---

## TL;DR (One‑page Goal)

Deliver a single GraphQL search endpoint that returns **grouped, ranked, case‑insensitive** results across multiple entity types with **prefix matching (type‑ahead)**, stable pagination primitives, and **p95 < 200 ms** latency for typical queries. Transport is **GraphQL over HTTP**; the frontend debounces (200–300 ms) and cancels in‑flight requests.

---

## Architecture Overview

**Pattern:** CQRS - this service is a **read‑model** only.

* **Indexer path (outside this service):** OLTP events -> Kafka -> *indexer‑search* -> **search_items** (read DB).
* **Query path (this service):** GraphQL `omnibox(q)` -> Postgres (**GIN FTS** + **pg_trgm**) -> grouped results by entity type.
* **Stateless:** no sessions, no websockets. GraphQL over HTTP only.
* **Tenancy:** tenant must be supplied via `X-Tenant` header; enforced in SQL WHERE clauses (and optionally by DB RLS if enabled in your environment).

> This service **does not** mutate data. It reads from a **read database** (separate from OLTP) that is continually updated by the *indexer‑search* consumer.

---

## Tech Stack

* **Java 21**, **Spring Boot 3.5.x**, **Spring GraphQL 1.4.x**
* **PostgreSQL 15+** with `pg_trgm` extension, `tsvector` FTS
* **Flyway** for DB migrations
* **Micrometer** for RED metrics
* **Gradle (Groovy)** build

---

## GraphQL API

# Print Schema
./gradlew :omnibox-search:printSchema
./gradlew :write-oltp:printSchema

### Single entrypoint

```graphql
# Query
type Query {
  omnibox(q: String!, limitPerGroup: Int = 5): OmniboxResult!
}

interface SearchHit {
  id: ID!
  title: String!
  subtitle: String
  score: Float!   # normalized [0..1]
}

type SearchHitContact implements SearchHit     { id: ID!, title: String!, subtitle: String, score: Float!, contactId: ID! }
type SearchHitListing implements SearchHit     { id: ID!, title: String!, subtitle: String, score: Float!, listingId: ID!, mlsId: String }
type SearchHitReferral implements SearchHit    { id: ID!, title: String!, subtitle: String, score: Float!, referralId: ID! }
type SearchHitTransaction implements SearchHit { id: ID!, title: String!, subtitle: String, score: Float!, transactionId: ID! }
type SearchHitProduct implements SearchHit     { id: ID!, title: String!, subtitle: String, score: Float!, productId: ID! }
type SearchHitMailing implements SearchHit     { id: ID!, title: String!, subtitle: String, score: Float!, mailingId: ID! }

type OmniboxResult {
  contacts:     [SearchHitContact!]!
  listings:     [SearchHitListing!]!
  referrals:    [SearchHitReferral!]!
  transactions: [SearchHitTransaction!]!
  products:     [SearchHitProduct!]!
  mailings:     [SearchHitMailing!]!
}
```

### Example query

```graphql
query Omnibox($q: String!, $limit: Int!) {
  omnibox(q: $q, limitPerGroup: $limit) {
    contacts     { id title subtitle score contactId }
    listings     { id title subtitle score listingId mlsId }
    referrals    { id title subtitle score referralId }
    transactions { id title subtitle score transactionId }
    products     { id title subtitle score productId }
    mailings     { id title subtitle score mailingId }
  }
}
```

**HTTP**: `POST /graphql`

**Headers**

* `Content-Type: application/json`
* `X-Tenant: <tenant-id>`  ← required

**Variables**

```json
{ "q": "sam", "limit": 5 }
```

---

## Query Semantics

* **Case‑insensitive**, whitespace tokenization.
* **Prefix matching** for the last token (`sam gal` -> `sam:* & gal:*`).
* **Very short queries** (≤2 chars): fallback to `ILIKE` + `pg_trgm` for responsiveness.
* **Ranking**: `ts_rank` weighted (title A > subtitle B) + secondary trigram similarity; ties broken by recency/id.
* **Visibility and ACL** are pre‑filtered in the read model (indexer ensures only active/visible records are present).

---

## Data Model (Read DB)

`search_items` is a **unified** table used for all entity types.

| column      | type      | notes                         |
| ----------- | --------- | ----------------------------- |
| id          | bigserial | PK (internal)                 |
| tenant_id   | text      | required                      |
| entity_type | text      | CONTACT/LISTING/…             |
| entity_id   | text      | source entity UUID (as text)  |
| title       | text      | search title (weighted A)     |
| subtitle    | text      | optional (weighted B)         |
| tsv         | tsvector  | generated from title/subtitle |

**Indexes**

* `GIN (tsv)`
* `GIN (lower(title) gin_trgm_ops)`
* `GIN (lower(subtitle) gin_trgm_ops)`
* `UNIQUE (tenant_id, entity_type, entity_id)` ← idempotent upserts

**Migration sketch** (simplified):

```sql
create extension if not exists pg_trgm;

create table if not exists search_items (
  id bigserial primary key,
  tenant_id text not null,
  entity_type text not null,
  entity_id text not null,
  title text not null,
  subtitle text,
  tsv tsvector generated always as (
    setweight(to_tsvector('english', coalesce(title,'')), 'A') ||
    setweight(to_tsvector('english', coalesce(subtitle,'')), 'B')
  ) stored,
  constraint ux_search unique (tenant_id, entity_type, entity_id)
);

create index if not exists idx_search_tsv on search_items using gin (tsv);
create index if not exists idx_search_title_trgm on search_items using gin (lower(title) gin_trgm_ops);
create index if not exists idx_search_subtitle_trgm on search_items using gin (lower(subtitle) gin_trgm_ops);
create index if not exists idx_search_tenant on search_items (tenant_id);
```

> The **indexer‑search** microservice performs upserts/deletes into `search_items` based on domain events from Kafka.

---

## Configuration

This service reads configuration from `application.yml` and optionally from a local `.env` file.

```
server:
  port: ${SEARCH_SERVER_PORT:8080}

spring:
  config:
    import:
      - "optional:file:.env[.properties]"
  datasource:
    url: jdbc:postgresql://${READ_DB_HOST:localhost}:${READ_DB_PORT:5442}/${READ_DB_NAME:omnibox}?currentSchema=public&sslmode=disable
    username: ${READ_DB_USER:omnibox}
    password: ${READ_DB_PASSWORD:omnibox}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 500

graphql:
  schema:
    # keep introspection enabled for frontend codegen in dev
    introspection:
      enabled: true

management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus

logging:
  level:
    com.github.dimitryivaniuta.gateway.search: INFO
```

**Important:**

* Ensure the **read DB** is reachable and already populated by the *indexer‑search* pipeline.
* The service requires `X-Tenant` header.

---

## Running Locally

1. **Start dependencies** (example Docker compose snippet)

    * Postgres for read model (port `5442`)
    * Kafka + indexer‑search (separate service) to populate `search_items`

2. **Build & run**

```bash
./gradlew :omnibox-search:clean :omnibox-search:bootRun
```

3. **Test the endpoint**

```bash
curl -s http://localhost:8080/graphql \
  -H 'Content-Type: application/json' \
  -H 'X-Tenant: demo-tenant' \
  --data '{
    "query":"query($q:String!,$n:Int!){ omnibox(q:$q, limitPerGroup:$n){ listings{ id title score listingId } contacts{ id title score contactId }}}",
    "variables":{"q":"sam","n":5}
  }' | jq .
```

---

## Observability

* **Metrics (Micrometer)**: `omnibox.db.timer` (DB round‑trip), RED counters.
* **Logging**: slow query logs (>150 ms) with token counts and chosen plan (FTS/ILIKE).
* **Tracing**: propagation via gateway (optional); annotate tokenize/plan/DB/marshal phases.

---

## Performance & SLOs

* **Targets**: p95 < 200 ms for 2–3 token queries; p99 < 400 ms.
* **Throughput**: 200 RPS sustained with graceful degradation.
* **Tuning**: `work_mem`, effective cache size, `pg_trgm.similarity_threshold (0.3–0.4)`; validate with `EXPLAIN (ANALYZE, BUFFERS)`.

---

## Security

* **Tenant isolation** via `X-Tenant` header -> SQL row filters: `where tenant_id = :tenant`.
* **(Recommended)** DB Row‑Level Security (RLS) depending on your platform.
* Sanitize query text before logging (mask emails/phones if needed).
* Bound `q` length (e.g., ≤100 chars); reject control characters.

---

## Frontend Integration

* GraphQL over HTTP at `/graphql`.
* Enable CORS in this service to allow your React dev server (`http://localhost:5173`) to introspect and query. See `SecurityConfig` in code.
* Works with GraphQL Code Generator + Apollo Client; example codegen configs are available in the project notes.

---

## Troubleshooting

**`Mutation must define one or more fields` during startup**

* Ensure only one SDL declares `type Mutation` base; others use `extend type Mutation`.

**`invalid schema` / multiple root `Query` definitions**

* Same rule: a single `_core.graphqls` provides `type Query` and `type Mutation`; feature files must `extend`.

**`Missing X-Tenant` or empty results**

* Send the `X-Tenant` header.
* Confirm `search_items` contains data for that tenant.

**Datasource connection refused**

* Verify `READ_DB_HOST/PORT/NAME/USER/PASSWORD` and the container port mapping.

**Slow queries**

* Check GIN/TRGM indexes exist; confirm `tsvector` generated column.

---

## Project Layout (selected)

```
omnibox-search/
  src/main/java/com/.../search/
    config/            # Security, CORS, etc.
    graphql/           # Query mapping controller (omnibox)
    repository/        # SearchRepository (JDBC)
    security/          # Tenant context/filter (if reused)
    service/           # OmniboxService (ranking/normalization)
    util/              # Tokenizer, ScoreNormalizer
  src/main/resources/
    application.yml
    graphql/
      schema.graphqls  # (omnibox schema)
```

---

## Contributing

* Keep SQL deterministic and idempotent; prefer explicit index names.
* All new code should include basic unit tests and log timings.
* Avoid leaking counts across tenants; never expose global totals.

---

## License

This project is part of the **search‑platform** stack. License as per repository root (TBD).
