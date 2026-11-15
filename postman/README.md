# CopMap Tactical Police API — Postman Collections & Interactive Demo

This directory contains the complete automated test suite and live interactive demo collection for the **CopMap Tactical Police Command & Real-Time Tracking Platform**.

---

## 📁 Files in this Directory

| File | Type | Description |
|---|---|---|
| [`CopMap_Live_Demo.postman_collection.json`](./CopMap_Live_Demo.postman_collection.json) | **Interactive Demo Collection** | **Every single URL is a REAL live URL** with concrete example IDs and pre-injected 24-hour Bearer tokens. Includes **all 45 saved demo responses** showing real requests, real headers, and formatted JSON output. |
| [`CopMap_API_Tests.postman_collection.json`](./CopMap_API_Tests.postman_collection.json) | **Automated CI/CD Test Suite** | 45 comprehensive end-to-end API test requests with 82 dynamic assertions and collection fallback variables. Passes 100% (82/82 assertions, 0 failures). |
| [`CopMap_Local.postman_environment.json`](./CopMap_Local.postman_environment.json) | **Postman Environment** | Target `http://localhost:8082` with all concrete entity IDs and active 24-hour JWT tokens. |
| [`test-run-output.txt`](./test-run-output.txt) | **Live Newman Execution Log** | Full raw terminal output of the latest automated Newman execution (82/82 passed). |
| [`test-run-report.json`](./test-run-report.json) | **Machine-Readable Report** | Newman JSON execution report with timestamps, HTTP latencies, headers, and payloads. |
| [`setup-postman-suite.js`](./setup-postman-suite.js) | **Maintenance Script** | Automation script used to sync demo examples and environment tokens from live database runs. |

---

## 🔍 Concrete Example IDs Used Across Collections

All requests in [`CopMap_Live_Demo.postman_collection.json`](./CopMap_Live_Demo.postman_collection.json) and default environment variables use verified entities present in the live database:

| Entity | Example Live ID / Value | Description |
|---|---|---|
| **Base URL** | `http://localhost:8082` | Active Spring Boot Backend |
| **Tactical Unit (Team 1)** | `6881c2dd-237b-474e-8d99-e6f384a8adbf` | *Alpha Tactical QRT - Sector 7612* |
| **Perimeter Unit (Team 2)** | `86233c9f-26ea-4a79-858c-65345d164e0b` | *Bravo Perimeter Security 7612* |
| **Beat Officer User ID** | `ada401f6-fd64-427b-be1a-644a31dcb8cc` | Sub-Inspector Anita Deshmukh (MySQL User) |
| **Beat Officer Profile ID** | `dcd3cb4f-ee31-44ee-96d9-f00e43775472` | Sub-Inspector Anita Deshmukh (PostgreSQL Profile) |
| **Senior Planner Officer ID** | `34f2fa84-ff65-414c-8f8a-96f93c9f9853` | Sr Inspector Vikram Rane (PostgreSQL Profile) |
| **Operation ID** | `41c39f43-d780-4483-9817-d7ff73d0f1c5` | *Operation Shield Bandobast 7612* (`ACTIVE`) |
| **Task ID** | `8e27d565-8374-4b7c-85ca-5b26d1d7a268` | *Arterial Nakabandi Checkpoint 7612* |
| **Assignment ID** | `3a10e8ab-4bd1-4351-9a86-14e783697a61` | Field Task Assignment (`COMPLETED`) |
| **Approval Request ID** | `2b3a1413-8bc7-4661-8941-3af4c93c119b` | Cross-Team Multi-Unit Joint Operation Approval |
| **Checkpoint Location ID** | `b8cebb22-1df9-41ed-8b49-33b60d7aba1d` | Wakad Expressway Toll Plaza Checkpoint |

---

## 🖥️ How to Use in Postman Desktop

### 1. Interactive Demo Walkthrough (`CopMap_Live_Demo.postman_collection.json`)
1. In Postman Desktop, click **Import** $\rightarrow$ select [`CopMap_Live_Demo.postman_collection.json`](./CopMap_Live_Demo.postman_collection.json).
2. Expand any folder in the left sidebar (e.g., `3. Tactical Teams Creation` or `6. Tactical Operations & Approvals`).
3. Click on any request:
   - Notice the **URL bar** displays the literal real URL:
     ```http
     GET http://localhost:8082/api/v1/teams/6881c2dd-237b-474e-8d99-e6f384a8adbf
     ```
   - Click **Send**: The backend responds immediately with `200 OK` and live tactical data!
4. **View All Demo Responses**:
   - Under each request, click the **Examples** dropdown in the upper right of the request pane (or click **View complete documentation** for the collection).
   - Postman renders the saved example showing the full request, headers, status code, and formatted JSON response payload.

### 2. Automated Test Suite (`CopMap_API_Tests.postman_collection.json`)
1. Import [`CopMap_API_Tests.postman_collection.json`](./CopMap_API_Tests.postman_collection.json) and [`CopMap_Local.postman_environment.json`](./CopMap_Local.postman_environment.json).
2. In the top-right environment picker, select **CopMap Local Environment**.
3. Right-click the collection $\rightarrow$ **Run Collection** $\rightarrow$ Click **Run CopMap Tactical Police API**.
4. All 45 requests execute in sequence, chaining tokens and IDs dynamically, validating all 82 test assertions.

---

## 🚀 Running via Newman CLI (Headless)

From the project root:

```powershell
npx newman run postman/CopMap_API_Tests.postman_collection.json `
  -e postman/CopMap_Local.postman_environment.json `
  --reporters cli,json `
  --reporter-json-export postman/test-run-report.json
```

### Latest Execution Metrics
```text
┌─────────────────────────┬───────────────────┬───────────────────┐
│                         │          executed │            failed │
├─────────────────────────┼───────────────────┼───────────────────┤
│              iterations │                 1 │                 0 │
├─────────────────────────┼───────────────────┼───────────────────┤
│                requests │                45 │                 0 │
├─────────────────────────┼───────────────────┼───────────────────┤
│            test-scripts │                45 │                 0 │
├─────────────────────────┼───────────────────┼───────────────────┤
│      prerequest-scripts │                45 │                 0 │
├─────────────────────────┼───────────────────┼───────────────────┤
│              assertions │                82 │                 0 │
├─────────────────────────┴───────────────────┴───────────────────┤
│ total run duration: 6.6s                                        │
├─────────────────────────────────────────────────────────────────┤
│ total data received: 49.34kB (approx)                           │
├─────────────────────────────────────────────────────────────────┤
│ average response time: 67ms [min: 19ms, max: 225ms, s.d.: 52ms] │
└─────────────────────────────────────────────────────────────────┘
```
