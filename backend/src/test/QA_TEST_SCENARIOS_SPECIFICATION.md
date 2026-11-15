# CopMap (CMP) — Senior QA Test Scenarios Specification

**Document Version:** 1.0.0  
**Author:** Senior QA Engineering Lead  
**Scope:** Core Configuration, Dual-Database Routing, Connection Pooling, Distributed Redis Caching, OpenAPI Documentation, and Tactical Domain Features.  
**Execution Gate:** Scenarios defined for review. Automated implementation pending stakeholder sign-off.

---

## 1. Scope & Test Methodology

This document outlines the test strategy, unit test matrices, failure-injection scenarios, and integration test specifications. Tests are derived independently from law enforcement command-and-control business requirements, operational resilience goals, and structural architectural constraints.

### Target Technology Matrix
- **Unit & Component Testing:** JUnit 5 (Jupiter), AssertJ
- **Mocking & Isolation:** Mockito 5
- **HTTP / MVC Security & Web Slice:** Spring MockMvc, REST Assured
- **Containerized Integration Testing:** Testcontainers (PostgreSQL 16, Redis 7)
- **External Mocking & Contract Testing:** WireMock
- **Chaos & Fault Injection:** Docker network emulation (`tc netem` / Toxiproxy), connection pool exhaustion harnesses
- **Performance & Load:** k6
- **Code Coverage & Quality:** JaCoCo, SonarQube

---

## 2. Configuration & Infrastructure Test Scenarios

### 2.1 Connection Pooling & Database Resilience (HikariCP — MySQL & PostgreSQL)

#### Test Case Suite: `CFG-POOL-01` to `CFG-POOL-06`

| ID | Test Scenario | Test Type | Method / Tool | Expected Behavior & Assertion |
| :--- | :--- | :--- | :--- | :--- |
| `CFG-POOL-01` | **Default Pool Configuration Verification** | Unit / Spring Test | `HikariDataSource` inspection via JUnit 5 | Validate `maximumPoolSize`, `minimumIdle`, `idleTimeout`, and `connectionTimeout` (30000ms) match configuration for both MySQL and PostgreSQL pools. |
| `CFG-POOL-02` | **HikariCP Connection Pool Exhaustion** | Stress / Concurrency | Java `ExecutorService` (30 concurrent threads vs pool size 10) holding connections for 35s | 10 worker threads acquire connections; the remaining 20 wait up to 30,000ms. Subsequent callers throw `SQLTransientConnectionException: Connection is not available, request timed out after 30000ms`. Verify no deadlocks and graceful `500`/`503` envelope. |
| `CFG-POOL-03` | **Pool Recovery After Exhaustion** | Recovery Test | Release held connections after 31s | Once blocked threads release connections, queued requests successfully acquire connections. Pool returns to `idleConnections == minimumIdle`. |
| `CFG-POOL-04` | **Slow Database Simulation (PostgreSQL Latency)** | Fault Injection | `docker exec copmap-postgres psql -U postgres -c "SELECT pg_sleep(15);"` or toxiproxy 10s latency | Application connection acquisition waits without socket leak; HTTP requests respect timeout limits and return clean error responses without hanging web server worker threads. |
| `CFG-POOL-05` | **Database Process Termination & Pool Recovery** | Chaos Test | `docker stop copmap-postgres` $\rightarrow$ wait 10s $\rightarrow$ `docker start copmap-postgres` | While stopped: operations repository throws identifiable connection error. When restarted: HikariCP connection test query validates new sockets; application resumes normal query execution without requiring a restart. |
| `CFG-POOL-06` | **Connection Leak Detection** | Static / Runtime Audit | Set `leakDetectionThreshold = 20000` (20s) | Intentionally open connection without closing in test method. Verify Hikari logs: `Apparent connection leak detected`. Ensure all production repository queries cleanly return connections via Spring transaction management. |

---

### 2.2 Multi-Database Routing & Transaction Isolation

#### Routing Architecture Verification: Separate vs. Dynamic
* **Current Implementation:** **Separate Physical Routing** via package-bounded `EntityManagerFactory` and distinct transaction managers (`mysqlTransactionManager` for `auth.entities`, `postgresTransactionManager` for tactical entities).

#### Test Case Suite: `CFG-ROUT-01` to `CFG-ROUT-05`

| ID | Test Scenario | Test Type | Method / Tool | Expected Behavior & Assertion |
| :--- | :--- | :--- | :--- | :--- |
| `CFG-ROUT-01` | **Persistence Unit Boundary Enforcement** | Unit / Integration | Spring Context reflection | Verify `MySqlDatabaseConfig` manages only `com.copmap.auth.entities` and `PostgresDatabaseConfig` manages only `officer`, `operations`, `team`, and `tracking` entities. |
| `CFG-ROUT-02` | **Cross-Database Entity Leakage Prevention** | Integration | MockMvc / Service Test | Attempt to persist an `OfficerProfile` entity via a MySQL repository bean or a `User` entity via PostgreSQL repository. Must fail at compile-time/runtime with `IllegalArgumentException: Not a managed type`. |
| `CFG-ROUT-03` | **Dual-Transaction Manager Isolation** | Integration | JUnit 5 `@Transactional` | Execute a method annotated with `@Transactional("postgresTransactionManager")`. Induce a rollback in PostgreSQL. Verify PostgreSQL rolls back cleanly while MySQL connection is not affected or held open. |
| `CFG-ROUT-04` | **Simultaneous Dual-Datasource Read** | Integration | `UserServiceImpl.createUser()` test | Single flow writes to MySQL (`userRepository.save()`) and PostgreSQL (`officerRepository.save()`). Verify both stores reflect the created entity with matching `mysqlUserId`. |
| `CFG-ROUT-05` | **Partial Failure Handling (PostgreSQL down during MySQL write)** | Resilience Test | Stop PostgreSQL, trigger user registration | User registration completes in MySQL or throws transaction exception. Verify whether compensation logic or fallback creates officer profile on next login via `CurrentOfficerService`. |

---

### 2.3 Distributed Caching & Redis Fault Tolerance

#### Test Case Suite: `CFG-REDIS-01` to `CFG-REDIS-14`

| ID | Test Scenario | Chaos Action / Trigger | Expected Behavior & Assertion |
| :--- | :--- | :--- | :--- |
| `CFG-REDIS-01` | **Stop Redis Container** | `docker stop copmap-redis` | When Redis stops, `GET /api/v1/officers` must catch `RedisConnectionFailureException`, fall back to PostgreSQL, and return the directory (degraded mode, no 500 error). `LocationTrackingService` logs warning and saves telemetry directly to PostgreSQL. |
| `CFG-REDIS-02` | **Kill Redis Process** | `docker exec copmap-redis killall -9 redis-server` | Telemetry ingestion mid-stream handles socket reset cleanly. Subsequent calls retry or fall back to DB. |
| `CFG-REDIS-03` | **Disconnect Docker Network** | `docker network disconnect bridge copmap-redis` | Redis client experiences `QueryTimeoutException` or socket connect timeout. App thread pool does not get exhausted. |
| `CFG-REDIS-04` | **Block Redis Port (6379)** | Block port via firewall / iptables | Request fails fast on Redis operations without cascading thread lock; fallback queries execute against PostgreSQL. |
| `CFG-REDIS-05` | **Introduce Network Latency** | Add 2000ms latency via Toxiproxy | Lettuce connection timeout fires after configured timeout (e.g., 500ms). Web request completes within SLA using DB fallback. |
| `CFG-REDIS-06` | **Introduce Packet Loss** | 50% packet drop on port 6379 | System tolerates intermittent drops; read retries or serves from DB. |
| `CFG-REDIS-07` | **Exhaust Redis Connections** | Spawn 10,000 dummy clients to exceed `maxclients` | App connection pool encounters rejection; graceful degradation to PostgreSQL without server crash. |
| `CFG-REDIS-08` | **Exhaust Redis Memory** | Set `maxmemory 5mb` and fill with test keys | Verify memory eviction policy (`volatile-lru` or `allkeys-lru`). Telemetry writes continue or handle `OOM command not allowed` without failing the PostgreSQL insert. |
| `CFG-REDIS-09` | **Delete Cache Keys (Simulate Cache Miss)** | `redis-cli DEL officers_directory::all` | Next call to `GET /api/v1/officers` queries PostgreSQL, returns full list, and repopulates key `officers_directory::all` in Redis with 10-minute TTL. |
| `CFG-REDIS-10` | **Corrupt Cached Value** | `redis-cli SET officers_directory::all "INVALID_JSON_CORRUPTED"` | Generic Jackson deserializer throws `SerializationException`. Application catches error, evicts corrupted key, reloads from PostgreSQL, and repopulates valid JSON. |
| `CFG-REDIS-11` | **Cache Stampede / Dog-piling** | 100 concurrent threads request `GET /api/v1/officers` when key is absent | Verify whether single-flight locking or database connection pool handles the spike. Measure database query count. |
| `CFG-REDIS-12` | **Repeated Redis Restarts** | Loop restart Redis 5 times over 30s | Lettuce client automatically reconnects once socket is restored without requiring Spring Boot application restart. |
| `CFG-REDIS-13` | **Failover Simulation** | Switch from primary to replica | Connection pool updates topology; subsequent reads/writes succeed seamlessly. |
| `CFG-REDIS-14` | **Stale Cache & Manual Eviction** | Update PostgreSQL officer directly via SQL; then call `POST /api/v1/officers/cache/evict` | Calling eviction endpoint purges `officers_directory::all`. Next fetch immediately returns updated PostgreSQL value. |

---

### 2.4 API Documentation Configuration (`APIDocConfig.java` / OpenAPI)

#### Test Case Suite: `CFG-DOC-01` to `CFG-DOC-04`

| ID | Test Scenario | Method / Tool | Expected Behavior & Assertion |
| :--- | :--- | :--- | :--- |
| `CFG-DOC-01` | **OpenAPI Specification Availability** | MockMvc `GET /v3/api-docs` | HTTP 200 OK; returns valid OpenAPI 3.0 JSON containing info: title "COPMAP APPLICATION", contact "Anant Gupta". |
| `CFG-DOC-02` | **Swagger UI Asset Access Without Auth** | MockMvc `GET /swagger-ui/index.html` | HTTP 200 OK; verify `/swagger-ui/**` is whitelisted in `AppConstants.AUTH_PUBLIC_URLS` and not intercepted by JWT filter. |
| `CFG-DOC-03` | **Security Scheme Definition in Spec** | Inspect `/v3/api-docs` components | Verify `components.securitySchemes` defines `bearer-jwt` (Type: HTTP, Scheme: bearer, BearerFormat: JWT). |
| `CFG-DOC-04` | **Tactical Endpoint Inventory Verification** | Automated JSON Schema check | All tactical endpoints (`/api/v1/teams`, `/api/v1/operations`, `/api/v1/tasks`, `/api/v1/tracking`, `/api/v1/officers`) are documented with request/response schemas. |

---

## 3. Feature-Level Test Scenarios (Business Requirements Driven)

### 3.1 Happy Paths

#### Test Case Suite: `FEAT-HP-01` to `FEAT-HP-08`

| ID | Test Scenario | Steps | Expected Outcome |
| :--- | :--- | :--- | :--- |
| `FEAT-HP-01` | **Officer Registration with Instant PG Sync** | `POST /api/v1/auth/register` with valid email, password, policePost | HTTP 201 Created. User exists in MySQL; `OfficerProfile` exists in PostgreSQL with identical name/post; Redis cache is evicted. |
| `FEAT-HP-02` | **Supervisor Tactical Unit Creation** | Authenticate as Supervisor $\rightarrow$ `POST /api/v1/teams` with callsign "EAGLE-1" | HTTP 201 Created. Team persisted in PostgreSQL, initial `TeamAuditLog` record created with action `TEAM_CREATED`. |
| `FEAT-HP-03` | **Tactical Roster Assignment** | `POST /api/v1/teams/{id}/members` with `officerId` and role `SNIPER` | HTTP 201 Created. `TeamMembership` created with `isActive=true`; audit log logged. |
| `FEAT-HP-04` | **Bandobast Operation Creation & Approval** | Senior Planner creates operation $\rightarrow$ Supervisor reviews with `decision="APPROVED"` | Operation status transitions: `DRAFT` $\rightarrow$ `PENDING_APPROVAL` $\rightarrow$ `APPROVED`. `OperationApprovalRequest` marked `APPROVED`. |
| `FEAT-HP-05` | **Geofenced Checkpoint Setup** | `POST /api/v1/operations/{id}/locations` with lat: 18.5204, lon: 73.8567, radius: 250m | HTTP 201 Created. Checkpoint perimeter stored with positive radius. |
| `FEAT-HP-06` | **Complete Task Lifecycle** | Lead assigns task $\rightarrow$ Officer starts (`IN_PROGRESS`) $\rightarrow$ Officer submits report (`SUBMITTED`) $\rightarrow$ Lead approves (`APPROVED`) | All four states transition in exact sequence with timestamps (`startedAt`, `submittedAt`, `reviewedAt`) recorded. |
| `FEAT-HP-07` | **20s GPS Telemetry Dual-Write** | `POST /api/v1/tracking/location` with valid GPS coordinates | HTTP 200 OK. Position saved in Redis `officer:live:<id>` with 24h TTL; row inserted in PostgreSQL `officer_location_updates`. |
| `FEAT-HP-08` | **Live Radar Freshness Evaluation** | Read `/api/v1/tracking/live` at 10s, 350s, and 950s after telemetry | Returns status `"FRESH"` (< 300s), `"DEGRADED"` (300s-900s), and `"STALE"` ($\ge$ 900s). |

---

### 3.2 Invalid Inputs & Schema Validation

#### Test Case Suite: `FEAT-VAL-01` to `FEAT-VAL-07`

| ID | Test Scenario | Input Trigger | Expected Response |
| :--- | :--- | :--- | :--- |
| `FEAT-VAL-01` | **Malformed Coordinates in Telemetry** | Latitude: `120.5` (> 90.0) or Longitude: `-200.0` (< -180.0) | HTTP 400 Bad Request (`ApiError`: "Latitude must be between -90 and 90"). |
| `FEAT-VAL-02` | **Negative Accuracy Radius** | `accuracyMeters = -5.0` | HTTP 400 Bad Request (`ApiError`: "Accuracy cannot be negative"). |
| `FEAT-VAL-03` | **Negative or Zero Team Capacity** | `maxMembers = 0` or `-5` | HTTP 400 Bad Request (`ApiError` with validation error map). |
| `FEAT-VAL-04` | **Blank Callsign or Title** | `callsign = ""` or `" "` | HTTP 400 Bad Request (`ApiError`: "Callsign must not be blank"). |
| `FEAT-VAL-05` | **Malformed JSON Request Body** | Truncated JSON `{"email": "test@` | HTTP 400 Bad Request (`ApiError`: "Malformed JSON request payload"). |
| `FEAT-VAL-06` | **Invalid UUID Path Parameter** | `GET /api/v1/teams/NOT-A-UUID` | HTTP 400 Bad Request (`ApiError`: "Parameter 'id' should be of type UUID"). |
| `FEAT-VAL-07` | **Invalid Operation Date Window** | `endTime` is before `startTime` | HTTP 400 Bad Request (`ApiError`: "End time must be after start time"). |

---

### 3.3 Authentication & Role-Based Access Control (RBAC)

#### Test Case Suite: `FEAT-SEC-01` to `FEAT-SEC-08`

| ID | Test Scenario | Caller Credentials | Target Endpoint | Expected Response |
| :--- | :--- | :--- | :--- | :--- |
| `FEAT-SEC-01` | **Missing Authentication Header** | None | `GET /api/v1/teams` | HTTP 401 Unauthorized (`ApiError`: "Unauthorized Access"). |
| `FEAT-SEC-02` | **Expired JWT Token** | Expired Token (> 24h) | `GET /api/v1/operations` | HTTP 401 Unauthorized (`ApiError`: "JWT expired"). |
| `FEAT-SEC-03` | **Tampered Signature JWT** | Modified payload / wrong key | `GET /api/v1/officers` | HTTP 401 Unauthorized (`ApiError`: "Invalid JWT signature"). |
| `FEAT-SEC-04` | **Officer Creating a Team** | Field Officer Token | `POST /api/v1/teams` | HTTP 403 Forbidden (`ApiError`: "Forbidden Access"). |
| `FEAT-SEC-05` | **Officer Approving an Operation** | Field Officer Token | `POST /api/v1/operations/{id}/approvals/review` | HTTP 403 Forbidden. Only `Supervisor` authorized. |
| `FEAT-SEC-06` | **Non-Supervisor Cache Eviction** | Team Lead Token | `POST /api/v1/officers/cache/evict` | HTTP 403 Forbidden. Requires `Supervisor` or `Administrator`. |
| `FEAT-SEC-07` | **Cross-Team Radar Snooping** | Officer in Team A | `GET /api/v1/tracking/teams/{TeamB}/live` | HTTP 403 Forbidden (`ApiError`: "You are not authorized to view live locations for this team"). |
| `FEAT-SEC-08` | **Spoofed Officer Telemetry Identity** | Officer A sending `officerId: Officer B` in body | `POST /api/v1/tracking/location` | Ignores payload `officerId`; securely resolves identity strictly from authenticated JWT principal (`CurrentOfficerService`). |

---

### 3.4 Business Rule Violations

#### Test Case Suite: `FEAT-BR-01` to `FEAT-BR-06`

| ID | Test Scenario | Business Rule Enforced | Expected Response |
| :--- | :--- | :--- | :--- |
| `FEAT-BR-01` | **Team Maximum Member Overflow** | Team capacity is 10; attempt to add 11th member | HTTP 409 Conflict (`ApiError`: "Team has reached maximum member capacity of 10"). |
| `FEAT-BR-02` | **Duplicate Active Membership** | Add Officer X who is already an active member of Team Y | HTTP 409 Conflict (`ApiError`: "Officer is already a member of this team"). |
| `FEAT-BR-03` | **Illegal Task Status Jump** | Attempt to transition task directly from `ASSIGNED` to `APPROVED` (skipping `SUBMITTED`) | HTTP 409 Conflict (`ApiError`: "Cannot approve assignment that is not in SUBMITTED state"). |
| `FEAT-BR-04` | **Unauthorized Task Self-Approval** | Officer attempts to approve their own submitted task | HTTP 403 Forbidden (`ApiError`: "Officers cannot approve their own assignments"). |
| `FEAT-BR-05` | **Premature Operation Activation** | Transition operation to `ACTIVE` while still in `PENDING_APPROVAL` status | HTTP 409 Conflict (`ApiError`: "Operation must be APPROVED by supervisor before becoming ACTIVE"). |
| `FEAT-BR-06` | **Audit Retention Protection on Checkpoints** | Attempt to delete an `OperationLocation` that has active audit trail history | HTTP 409 Conflict (`ApiError`: "Cannot delete location with active audit trail records. Evidence retention policy enforced"). |

---

### 3.5 Database Consistency & Integrity

#### Test Case Suite: `FEAT-DB-01` to `FEAT-DB-04`

| ID | Test Scenario | Test Execution | Expected Consistency Result |
| :--- | :--- | :--- | :--- |
| `FEAT-DB-01` | **User Deletion Cascade Isolation** | Soft-delete / deactivate User in MySQL | PostgreSQL tactical records (`TaskAssignment`, `TeamMembership`) retain historical integrity; officer profile marked inactive without orphaned foreign key failure. |
| `FEAT-DB-02` | **Audit Trail Append-Only Immutability** | Attempt `DELETE` or `UPDATE` on `TeamAuditLog` / `OperationAuditLog` | No JPA update/delete methods exposed; table permissions prevent modification; audit logs remain permanent. |
| `FEAT-DB-03` | **Transactional Rollback on Mid-Batch Task Dispatch** | Assign 5 officers to a task; 4th officer ID does not exist | Entire transaction rolls back; zero assignments persisted in PostgreSQL; no partial state left behind. |
| `FEAT-DB-04` | **MySQL-PostgreSQL UUID Consistency** | Compare `users.id` in MySQL with `officer_profiles.mysql_user_id` in PostgreSQL | 100% 1-to-1 match across all registered police personnel. |

---

### 3.6 Concurrency & Duplicate Requests

#### Test Case Suite: `FEAT-CONC-01` to `FEAT-CONC-04`

| ID | Test Scenario | Concurrency Trigger | Expected Outcome |
| :--- | :--- | :--- | :--- |
| `FEAT-CONC-01` | **Simultaneous Last-Slot Team Recruitment** | Team has 1 slot left (`capacity = 10`, `current = 9`). Two supervisors concurrently add two different officers. | Exactly 1 request succeeds (HTTP 201); 1 request fails with HTTP 409 Conflict (Capacity reached). Total members = 10. |
| `FEAT-CONC-02` | **Double-Click on "Start Duty"** | Officer mobile fires two identical `POST .../start` within 50ms | First request transitions status to `IN_PROGRESS` (HTTP 200); second request returns idempotent HTTP 200 or clean HTTP 409 without corrupted timestamps. |
| `FEAT-CONC-03` | **Concurrent Telemetry Ingestion for Same Officer** | 5 threads post GPS updates simultaneously | Redis key reflects the newest timestamp; PostgreSQL stores all 5 breadcrumbs without primary key collisions. |
| `FEAT-CONC-04` | **Concurrent Operation Approval Reviews** | Two supervisors review same operation at the same time | Optimistic locking or database lock ensures single approval record is created; duplicate review rejected. |

---

### 3.7 Edge Cases

#### Test Case Suite: `FEAT-EDGE-01` to `FEAT-EDGE-06`

| ID | Test Scenario | Edge Condition | Expected Outcome |
| :--- | :--- | :--- | :--- |
| `FEAT-EDGE-01` | **Freshness Boundary Exact Timings** | Telemetry age at exactly: <br>• 299s <br>• 300s <br>• 899s <br>• 900s | Evaluates to: <br>• `FRESH` <br>• `DEGRADED` <br>• `DEGRADED` <br>• `STALE` |
| `FEAT-EDGE-02` | **Registration with Null or Blank Police Post** | `policePost = ""` or `null` | Defaults gracefully to `"HQ Dispatch"` without null pointer exception. |
| `FEAT-EDGE-03` | **Special Characters & SQLi / XSS in Task Notes** | Notes contain `<script>alert(1)</script>` or `'; DROP TABLE teams; --` | Correctly escaped by JPA parameterized queries; stored as literal string; safe for UI rendering. |
| `FEAT-EDGE-04` | **Max Length Boundaries** | Callsign with 255 characters, Task description with 10,000 characters | Accepted or rejected according to `@Size` annotations with clean `400 Bad Request` validation message. |
| `FEAT-EDGE-05` | **Antipodal & Prime Meridian GPS Coordinates** | `lat = 0.0, lon = 0.0`, `lat = 90.0, lon = 180.0`, `lat = -90.0, lon = -180.0` | Successfully ingested and rendered without arithmetic overflow or projection crashes. |
| `FEAT-EDGE-06` | **Officer Reassigned While on Active Task** | Officer removed from team while task is `IN_PROGRESS` | Existing assignment remains bound to historical officer profile; audit log records removal. |

---

### 3.8 Regression Risks

#### Test Case Suite: `FEAT-REG-01` to `FEAT-REG-04`

| ID | Test Scenario | Regression Risk Checked | Verification Steps |
| :--- | :--- | :--- | :--- |
| `FEAT-REG-01` | **Google OAuth Complete Removal Verification** | Verify removal of Google OAuth did not break standard local login | 1. Local login with email/password works.<br>2. Token refresh works.<br>3. `OAuth2SuccessHandler` cleanly rejects unsupported providers with error redirect without NPE. |
| `FEAT-REG-02` | **Postman Automated 45-Endpoint Suite** | Any changes must not break existing passing tests | Execute Newman CLI against collection: verify 45/45 requests and 82/82 assertions remain 100% passing. |
| `FEAT-REG-03` | **Dual-Database Hikari Connection Leak Check** | High load causing pool starvation | Run 500 requests across both DBs; verify active connection count returns to baseline idle count. |
| `FEAT-REG-04` | **Redis Outage Regression on Web Tier** | Redis container stop must not crash Spring context | Verify all endpoints return responses (either live or cached/DB fallback) without servlet container crashes. |

---

## 4. Test Execution & Docker Environment Plan

### Prerequisites & Cleanup Guarantee
1. **Docker Resources:** Test scenarios will interact with local Docker containers (`copmap-postgres` on port 5432 and `copmap-redis` on port 6379).
2. **Container Cleanup:** Any temporary containers, network disconnects, or mock proxies introduced during testing will be cleaned up so the environment remains in its original human-managed state.
3. **No Git Commit:** In accordance with constraints, no git commits will be made.

---

*Awaiting stakeholder approval of the above scenarios before writing automated test code.*
