# CopMap (CMP) — Tactical Police Command & Operations Platform

> **Confidential // Law Enforcement Operations System**  
> A mission-critical command, control, and coordination platform for police departments. Designed for real-time 20-second GPS telemetry tracking, automated Bandobast and Nakabandi perimeter management, strict 5-tier chain of command workflows, Redis-cached officer directory search, and court-admissible tactical PDF dossier generation.

---

## Table of Contents

1. [Executive Summary & Understanding](#1-executive-summary--understanding)
2. [Domain Concepts & Law Enforcement Operations](#2-domain-concepts--law-enforcement-operations)
3. [System Architecture & Design Decisions](#3-system-architecture--design-decisions)
   - [3.1 Dual-Database Isolation (MySQL + PostgreSQL)](#31-dual-database-isolation-mysql--postgresql)
   - [3.2 High-Velocity In-Memory Caching (Redis)](#32-high-velocity-in-memory-caching-redis)
   - [3.3 20-Second GPS Telemetry & Freshness Engine](#33-20-second-gps-telemetry--freshness-engine)
   - [3.4 Tactical PDF Dossier Generation](#34-tactical-pdf-dossier-generation)
   - [3.5 Responsive Mobile-First Tactical UI](#35-responsive-mobile-first-tactical-ui)
4. [Role Hierarchy & Chain of Command](#4-role-hierarchy--chain-of-command)
5. [Core Functional Modules & Workflows](#5-core-functional-modules--workflows)
   - [5.1 Real-Time Tactical Telemetry & Radar](#51-real-time-tactical-telemetry--radar)
   - [5.2 Tactical Operation Dispatch (Bandobast, Nakabandi, Patrolling)](#52-tactical-operation-dispatch-bandobast-nakabandi-patrolling)
   - [5.3 End-to-End Task Duty Lifecycle](#53-end-to-end-task-duty-lifecycle)
   - [5.4 Redis Officer Directory & Police Post Search](#54-redis-officer-directory--police-post-search)
   - [5.5 Immutable Governance & Audit Trail](#55-immutable-governance--audit-trail)
6. [Tech Stack](#6-tech-stack)
7. [Setup & Installation Instructions](#7-setup--installation-instructions)
   - [7.1 Prerequisites](#71-prerequisites)
   - [7.2 Database Setup (MySQL, PostgreSQL, Redis)](#72-database-setup-mysql-postgresql-redis)
   - [7.3 Backend Setup & Run (Spring Boot)](#73-backend-setup--run-spring-boot)
   - [7.4 Frontend Setup & Run (React + Vite)](#74-frontend-setup--run-react--vite)
8. [API & Endpoint Reference](#8-api--endpoint-reference)
9. [Verification & Testing](#9-verification--testing)

---

## 1. Executive Summary & Understanding

Modern police operations require instantaneous tactical clarity, strict accountability, and sub-second coordination between central control rooms, field commanders, and beat officers on the ground. Traditional police paper logs and fragmented messaging applications lead to signal lag, unverified duty check-ins, and insecure operational dispatches.

**CopMap (CMP)** addresses these operational challenges by providing:
- **Centralized Tactical Oversight**: A single pane of glass for real-time troop movement, landmark check-ins, and multi-team operations.
- **Automated GPS Telemetry Engine**: Continuous 20-second location beacons with automated signal freshness validation (Fresh, Degraded, Stale) and heuristic sector detection.
- **Hierarchical Governance**: Strict role enforcement dividing responsibilities among Supervisors, Senior Planners, Team Leads, Control Room Operators, and Field Beat Officers.
- **High-Velocity Directory Queries**: In-memory Redis caching enabling sub-10ms queries for officers filtered by Name, Rank, and assigned Police Post.
- **Court-Admissible Dossier Export**: Instantly compiled vector PDF dossiers with digital verification signatures, security classification headers, and complete audit records.

---

## 2. Domain Concepts & Law Enforcement Operations

CopMap is specifically structured around authentic law enforcement protocols:

| Police Operation Type | Description & Tactical Execution |
| :--- | :--- |
| **BANDOBAST** | Large-scale security deployment for VIP movement, public assemblies, festivals, or high-risk judicial hearings. Involves outer perimeter cordons, sterile corridors, observation posts, and multi-team manpower quotas. |
| **NAKABANDI** | High-alert vehicular interception and arterial road blockades. Features tactical funneling, spike strips (tyre deflators), breathalyzer inspection points, and direct radio communication with the Police Control Room. |
| **PATROLLING** | Routine beat surveillance, night rounds, and hotspot crime deterrence. Involves scheduled landmark check-ins and automated route history logging. |
| **GENERAL DUTY** | Station desk coverage, court escort, custody transport, and administrative police duties. |
| **INVESTIGATION** | Detective and forensic task forces dispatched to collect evidence, canvas neighborhoods, and record witness statements. |

---

## 3. System Architecture & Design Decisions

```
                           ┌──────────────────────────────────────────────┐
                           │          Tactical Frontend Client            │
                           │   React 19 + TypeScript + Vite + Tailwind    │
                           │  Interactive Radar + Leaflet Map + jsPDF     │
                           └──────────────────────┬───────────────────────┘
                                                  │ REST / JSON (JWT Bearer)
                                                  ▼
                           ┌──────────────────────────────────────────────┐
                           │          Spring Boot 3.4.x Backend           │
                           │    Spring Security + Dual JPA + Redis Cache  │
                           └──────────────┬───────────────┬───────────────┘
                                          │               │
                 ┌────────────────────────┴────────┐      │
                 ▼                                 ▼      ▼
    ┌─────────────────────────┐     ┌────────────────────────┐     ┌────────────────────────┐
    │     MySQL 8.x Database  │     │ PostgreSQL 16 Database │     │      Redis 7.x Cache   │
    │  - User Accounts        │     │  - Tactical Teams      │     │  - Officer Directory   │
    │  - Password Hashes      │     │  - Team Members        │     │  - Police Post Cache   │
    │  - JWT Auth Sessions    │     │  - Tactical Operations │     │  - <10ms Query Latency │
    │  - Roles & Permissions  │     │  - Dispatched Tasks    │     │  - Automatic Eviction  │
    │                         │     │  - GPS Telemetry Fixes │     │                        │
    │                         │     │  - Immutable Audit Logs│     │                        │
    └─────────────────────────┘     └────────────────────────┘     └────────────────────────┘
```

### 3.1 Dual-Database Isolation (MySQL + PostgreSQL)
- **Design Decision**: Identity credentials and sensitive authentication data are strictly decoupled from operational tactical data.
- **Implementation**:
  - **MySQL Persistence Unit (`primary`)**: Houses user accounts, encrypted password hashes (BCrypt), user roles, and OAuth2 security profiles.
  - **PostgreSQL Persistence Unit (`postgres`)**: Houses mission-critical spatial and operational data—teams, roster hierarchies, high-priority operations, tasks, assignments, live GPS locations, and audit trails.
- **Benefits**:
  - Prevents accidental exposure of password hashes or identity stores during operational data exports or third-party audits.
  - Enables independent horizontal scaling and optimized backup schedules for spatial telemetry tables.

### 3.2 High-Velocity In-Memory Caching (Redis)
- **Design Decision**: Police directory searches must return in sub-10ms times, even with thousands of officers across dozens of police stations.
- **Implementation**:
  - Officer directory records (including name, designation, police post, and email) are pre-loaded and cached in Redis under `officers::directory` and `officers::available`.
  - Cache entries are automatically evicted whenever a team member is added, removed, or reassigned.
  - Supervisors and Planners have access to an instant cache eviction trigger (`POST /officers/cache/evict`).

### 3.3 20-Second GPS Telemetry & Freshness Engine
- **Design Decision**: In high-risk field operations, delayed or stale GPS telemetry can endanger officer safety. A binary "online/offline" status is insufficient.
- **Implementation**:
  - Officers stream GPS fixes every 20 seconds.
  - Signals are categorized into a 3-tier freshness evaluation:
    - 🟢 **Live (Fresh)**: Received within $\le 30$ seconds. High-precision fix with $\pm 5\text{m}$ accuracy.
    - 🟡 **Degraded**: Received between $30$ and $60$ seconds ago. Indicates network latency or urban canyon obstruction.
    - 🔴 **Stale / Offline**: Older than $> 60$ seconds. Triggers a visual alert in the control room for lost telemetry.
  - Automated fast heuristic sector detection and reverse geocoding provide human-readable landmark names (e.g., "Wakad Flyover Beat") alongside raw coordinates.

### 3.4 Tactical PDF Dossier Generation
- **Design Decision**: Provide instant, court-admissible, client-side PDF export with zero server load or binary round-trip overhead.
- **Implementation**:
  - Implemented via `jsPDF` and `jspdf-autotable` in `pdfReportService.ts`.
  - Generates two dedicated official dossier formats:
    1. **Full Tactical Unit Dossier**: High-priority operations, tasks matrix, deployed officer list, telemetry status, command verification block, and official classification headers (`CONFIDENTIAL // LAW ENFORCEMENT SENSITIVE`).
    2. **Individual Officer Service Dossier**: Personal duty logs, assigned responsibilities, start/completion timestamps, filed field reports, telemetry snapshot, and supervisory sign-off seals.

### 3.5 Responsive Mobile-First Tactical UI
- **Design Decision**: Tactical personnel in police cruisers or on foot use rugged tablets and mobile devices, while commanders operate multi-monitor control desks.
- **Implementation**:
  - Engineered with Tailwind CSS v4 and fluid breakpoints (`sm`, `md`, `lg`, `xl`).
  - Mobile drawer navigation with backdrop blur and touch-optimized controls (`h-11` inputs, `min-w-[440px]` scrollable tabs).
  - Leaflet maps include auto-invalidation listeners (`map.invalidateSize()`) on screen rotation and window resizing.
  - All operational data tables are wrapped in responsive horizontal scroll viewports with responsive action stacking.

---

## 4. Role Hierarchy & Chain of Command

CopMap enforces strict hierarchical access control. Every user action is audited against their authenticated role:

```
                            ┌─────────────────────────────────┐
                            │      DIVISION SUPERVISOR        │
                            │   Central Division Command HQ   │
                            └────────────────┬────────────────┘
                                             │
                       ┌─────────────────────┴─────────────────────┐
                       ▼                                           ▼
         ┌───────────────────────────┐               ┌───────────────────────────┐
         │   SENIOR PLANNER          │               │      TEAM LEAD            │
         │   Strategic Operations    │               │  Field Ground Commander   │
         │   & Task Dispatcher       │               │  & Duty Verifier          │
         └─────────────┬─────────────┘               └─────────────┬─────────────┘
                       │                                           │
                       └─────────────────────┬─────────────────────┘
                                             │
                       ┌─────────────────────┴─────────────────────┐
                       ▼                                           ▼
         ┌───────────────────────────┐               ┌───────────────────────────┐
         │   CONTROL OPERATOR        │               │      FIELD OPERATOR       │
         │   24/7 Map Telemetry      │               │  Beat / Ground Officer    │
         │   & Dispatch Monitor      │               │  Executes Tasks & Reports │
         └───────────────────────────┘               └───────────────────────────┘
```

| Role | Badges / Insignia | Core Responsibilities | Report & Dossier Access |
| :--- | :--- | :--- | :--- |
| **SUPERVISOR** | Ashok Stambh / Star | Highest authority. Approves cross-team joint operations, inspects division-wide audit trails, and manages executive redeployments. | Full access to all unit dossiers, officer dossiers, and audit exports. |
| **SENIOR_PLANNER** | Three Stars / Laurel | Creates tactical operations (Bandobast, Nakabandi), sets manpower quotas, dispatches tasks, and manages unit rosters. | Full access to team operational dossiers and officer dossiers. |
| **TEAM_LEAD** | Two Stars | Field commander. Deploys officers to specific checkpoints, monitors real-time 20s telemetry, reviews field reports, and approves duty completion. | Authorized to download unit operational dossiers and officer dossiers. |
| **CONTROL_OPERATOR** | Radar / Radio Crest | 24/7 control room operator. Monitors Leaflet tactical radar, tracks GPS freshness, and coordinates emergency coordinates. | Authorized to download live unit telemetry and operational dossiers. |
| **FIELD_OPERATOR** | Police Shield | Beat officer on the ground. Accepts task dispatches, initiates duty with GPS location tagging, and files field incident reports. | Authorized to view and export personal duty and service dossiers. |

---

## 5. Core Functional Modules & Workflows

### 5.1 Real-Time Tactical Telemetry & Radar
- **Live Tracking View**: Interactive Leaflet map displaying active police officers as color-coded radar beacons.
- **Signal Freshness Gauges**: Real-time status indicators (🟢 Live, 🟡 Degraded, 🔴 Stale).
- **Officer Map Modal**: Detailed popup providing exact coordinates (Lat/Lng), accuracy circle radius ($\pm \text{meters}$), human-readable sector detection, and one-click Google Maps / coordinate copying.

### 5.2 Tactical Operation Dispatch (Bandobast, Nakabandi, Patrolling)
- **Creation & Scoping**: Planners establish operations with operational types, start/end schedules, required manpower quotas, and briefing notes.
- **Cross-Team Authorizations**: When an operation requires assistance from neighboring police stations, formal approval requests are routed to the Division Supervisor.
- **Execution Workflow**: `DRAFT` $\rightarrow$ `PLANNED` $\rightarrow$ `ACTIVE` $\rightarrow$ `PAUSED` $\rightarrow$ `COMPLETED`.

### 5.3 End-to-End Task Duty Lifecycle
Every tactical task follows an audited four-stage lifecycle:
1. **`ASSIGNED`**: Planner or Team Lead deploys an officer to a specific task (e.g., "Lead Patrol", "Gate 2 Checkpoint") with operational instructions.
2. **`IN_PROGRESS`**: Officer clicks **Start Duty** upon arriving at the designated landmark; starting timestamp and GPS coordinates are recorded.
3. **`SUBMITTED`**: Officer files a field completion report detailing patrol route, vehicle inspections, and incident findings.
4. **`COMPLETED`**: Team Lead reviews the field report, enters verification remarks, and marks the task as completed.

### 5.4 Redis Officer Directory & Police Post Search
- **Cached Roster Lookup**: Filter officers instantly by typing name, designation, or Police Post (e.g., Wakad, Hinjewadi, Shivajinagar).
- **Fast Recruitment**: Add officers to tactical units in a single click with pre-assigned unit roles.

### 5.5 Immutable Governance & Audit Trail
- All membership changes, task state transitions, and operation status updates trigger an append-only audit record in PostgreSQL (`team_audit`).
- Records include actor identity (`changed_by_id`), action type, old values, new values, timestamps, and supervisory remarks.

---

## 6. Tech Stack

### Frontend
- **Framework**: React 19 + TypeScript + Vite
- **Styling**: Tailwind CSS v4 + Tailwind Animate CSS
- **Routing & State**: React Router 7 + Zustand (Auth Token persistence)
- **Mapping**: Leaflet 1.9 + OpenStreetMap Tiles
- **PDF Generation**: jsPDF 4.2 + jsPDF-AutoTable 5.0
- **Animations & Icons**: Framer Motion 12 + Lucide React
- **Notifications**: React Hot Toast

### Backend
- **Framework**: Spring Boot 3.4.x (Java 21)
- **Security**: Spring Security 6 with Stateless JWT Bearer Filter + Role-Based Access Control
- **Persistence**: Spring Data JPA with Dual DataSources:
  - MySQL Connector/J
  - PostgreSQL Driver
- **In-Memory Cache**: Spring Data Redis (Lettuce client)
- **Build Tool**: Apache Maven Wrapper (`mvnw`)

---

## 7. Setup & Installation Instructions

### 7.1 Prerequisites
Ensure the following software is installed on your development workstation:
- **Java Development Kit (JDK)**: Version 21 or higher
- **Node.js**: Version 20.x or higher (with `npm`)
- **MySQL Server**: Version 8.0 or higher (Port `3306`)
- **PostgreSQL Server**: Version 16.x or higher (Port `5432`)
- **Redis Server**: Version 7.x or higher (Port `6379`)

---

### 7.2 Database Setup (MySQL, PostgreSQL, Redis)

#### 1. MySQL Setup
Open your MySQL terminal or client and execute:
```sql
CREATE DATABASE IF NOT EXISTS auth_app_db;
```

#### 2. PostgreSQL Setup
Open your PostgreSQL terminal (`psql`) and execute:
```sql
CREATE DATABASE auth_app_pg_db;
```

#### 3. Redis Setup
Start the local Redis server:
```bash
redis-server
```
Verify connectivity:
```bash
redis-cli ping
# Output should be: PONG
```

---

### 7.3 Backend Setup & Run (Spring Boot)

1. **Navigate to the Backend Directory**:
   ```powershell
   cd D:\Project\cmp\backend
   ```

2. **Configure Database Credentials**:
   Inspect `src/main/resources/application.yml` and ensure your database credentials match:
   ```yaml
   spring:
     datasource:
       url: jdbc:mysql://localhost:3306/auth_app_db?createDatabaseIfNotExist=true
       username: root
       password: your_mysql_password
     data:
       redis:
         host: localhost
         port: 6379
   
   postgres:
     datasource:
       url: jdbc:postgresql://localhost:5432/auth_app_pg_db
       username: postgres
       password: your_postgres_password
   ```

3. **Build and Run the Application**:
   ```powershell
   # Windows PowerShell
   .\mvnw.cmd spring-boot:run
   ```
   *The backend will initialize both database schemas and start listening on port **8082**.*

---

### 7.4 Frontend Setup & Run (React + Vite)

1. **Navigate to the Frontend Directory**:
   ```powershell
   cd D:\Project\cmp\frontend
   ```

2. **Install Dependencies**:
   ```powershell
   npm install
   ```

3. **Verify TypeScript Compilation**:
   ```powershell
   npm run build
   ```

4. **Start the Development Server**:
   ```powershell
   npm run dev
   ```
   *The tactical frontend will be accessible at: **`http://localhost:5173`**.*

---

## 8. API & Endpoint Reference

### Authentication & Identity (MySQL)
| Method | Endpoint | Description | Access |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/auth/login` | Authenticate with email/password; returns JWT | Public |
| `POST` | `/api/v1/auth/register` | Register officer; supports supervisor checkbox | Public |
| `GET` | `/api/v1/auth/me` | Fetch authenticated officer profile and role | Authenticated |

### Tactical Teams & Hierarchy (PostgreSQL)
| Method | Endpoint | Description | Access |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/teams` | List teams accessible by the authenticated user | Authenticated |
| `POST` | `/api/v1/teams` | Create a new tactical unit | Supervisor / Planner |
| `GET` | `/api/v1/teams/{id}` | Get detailed team metadata and role in team | Team Member |
| `GET` | `/api/v1/teams/{id}/members`| List assigned officers and team roles | Team Member |
| `POST` | `/api/v1/teams/{id}/members`| Add an officer to unit (evicts Redis cache) | Planner / Lead |
| `DELETE`| `/api/v1/teams/{id}/members/{officerId}` | Remove an officer from unit | Planner / Lead |
| `GET` | `/api/v1/teams/{id}/audit` | View immutable audit trail for team | Team Member |

### Tactical Tasks & Duty Assignments (PostgreSQL)
| Method | Endpoint | Description | Access |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/tasks` | Get dispatched tasks for a team | Team Member |
| `POST` | `/api/v1/tasks` | Dispatch a tactical task with location & orders | Planner / Lead |
| `POST` | `/api/v1/tasks/assign` | Assign officer to task with specific duties | Planner / Lead |
| `POST` | `/api/v1/tasks/assignments/{id}/start` | Officer begins duty; logs start timestamp | Assigned Officer |
| `POST` | `/api/v1/tasks/assignments/{id}/submit` | Officer submits field completion report | Assigned Officer |
| `POST` | `/api/v1/tasks/assignments/{id}/approve` | Team Lead endorses completed report | Lead / Supervisor |

### Tactical Operations (PostgreSQL)
| Method | Endpoint | Description | Access |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/operations` | List operations (Bandobast, Nakabandi, etc.) | Team Member |
| `POST` | `/api/v1/operations` | Create a high-priority operation | Planner / Lead |
| `PATCH`| `/api/v1/operations/{id}/status` | Update status (DRAFT, ACTIVE, COMPLETED) | Planner / Lead |
| `GET` | `/api/v1/operations/approvals` | List cross-team approval requests | Supervisor |
| `POST` | `/api/v1/operations/approvals/{id}/approve` | Supervisor authorizes joint operation | Supervisor |
| `POST` | `/api/v1/operations/approvals/{id}/reject` | Supervisor rejects joint operation | Supervisor |

### Real-Time GPS Tracking & Directory (PostgreSQL & Redis)
| Method | Endpoint | Description | Access |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/tracking/teams/{teamId}/live` | Get latest 20s GPS fixes for unit | Team Member |
| `POST` | `/api/v1/tracking/update` | Ingest officer GPS telemetry fix | Field Officer |
| `GET` | `/api/v1/officers/available` | Fast Redis-cached available officer directory | Planner / Supervisor |
| `POST` | `/api/v1/officers/cache/evict` | Flush Redis officer directory cache | Supervisor / Admin |

---

## 9. Verification & Testing

### Frontend Build Verification
Execute the production build to verify zero TypeScript errors and optimal bundling:
```powershell
cd D:\Project\cmp\frontend
npm run build
```
Expected output:
```
✓ built in ~7s
dist/index.html               0.49 kB
dist/assets/index.css         90.96 kB
dist/assets/index.js        1,254.12 kB
```

### Automated API Testing (Postman & Newman)
A full-coverage end-to-end API test suite (45 requests, 82 assertions) is available in the [`postman/`](file:///D:/Project/cmp/postman) folder:
```bash
# Execute Newman headless CLI against the live backend
npx newman run postman/CopMap_API_Tests.postman_collection.json \
  -e postman/CopMap_Local.postman_environment.json \
  --reporters cli,json \
  --reporter-json-export postman/test-run-report.json
```
For full collection documentation and execution reports, see [`postman/README.md`](file:///D:/Project/cmp/postman/README.md).

### Manual Testing Scenarios
1. **Interactive Home Page**:
   - Navigate to `http://localhost:5173`.
   - Scroll to the **Live Tactical Command Simulator**.
   - Click **Simulate GPS Ping** to watch live radar telemetry updates and signal freshness indicators.
   - Switch between **Bandobast Dispatch**, **Duty Lifecycle**, and **Redis Directory Search** tabs.
2. **Tactical Dossier PDF Export**:
   - Sign in with an authorized account (Supervisor, Planner, or Lead).
   - Navigate to a team's detail page (`/dashboard/teams/:teamId`).
   - Click **Download Unit Dossier (PDF)** in the header banner to inspect the compiled document.
   - Click **Dossier PDF** next to an individual officer in the Roster table to inspect their individual service record.
3. **Supervisor Registration & Chain of Command**:
   - Register a new account with the **Supervisor** checkbox enabled.
   - Sign in and verify access to cross-team authorization approvals and comprehensive dossier generation.

---

*CopMap Tactical Command System &bull; Official Law Enforcement Operations Platform &bull; All Rights Reserved.*
