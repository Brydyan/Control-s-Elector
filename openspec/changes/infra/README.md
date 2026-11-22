# Infrastructure Plan MVP 2027 - SDD Methodology

## Overview

This directory contains the complete infrastructure plan for Control Electoral MVP 2027, following the SDD (Structured Design Document) methodology proven in previous Control Electoral tasks.

Five critical infrastructure tasks, sequentially ordered by dependency. Total estimated execution: 4.5 days (26–36 hours work).

## Tasks

### 1. ODD #503: Domain Setup (4–6h, Day 0.5)
**Status**: Skeleton ready for Builder

Directory: `2026-11-08-odd-503-domain-setup/`

Establishes DNS infrastructure and environment variables for staging/production.
- Cloudflare DNS (A records → Railway, CNAME → Cloudflare Pages)
- Wildcard SSL certificates
- Railway + Cloudflare Pages env vars injection
- Health check endpoints

**Files**:
- `proposal.md` — Problem + scope + criticality
- `design.md` — Design decisions (D1–D4)
- `specs/domain/spec.md` — Requirements (RF-DOM-01 to RNF-DOM-01)
- `tasks.md` — Executable sections A–G with mutation tests
- `apply-progress.md` — Progress template (fill during execution)

**Blocker**: None (first task)

---

### 2. T-IN-13: Neon Database (6–8h, Day 1)
**Status**: Skeleton ready for Builder

Directory: `2026-11-08-t-in-13-neon-database/`

Production PostgreSQL via Neon serverless.
- Project creation + region alignment
- Liquibase migrations execution (idempotent)
- Connection pooling (HikariCP)
- Automated backups + PITR
- Health check DB endpoint

**Files**:
- `proposal.md` — Problem + scope
- `design.md` — Design decisions (D1–D5)
- `specs/database/spec.md` — Requirements (RF-DB-01 to RNF-DB-03)
- `tasks.md` — Sections A–H with mutation tests
- `apply-progress.md` — Progress template

**Blocker**: ODD #503 (env vars on Railway)

---

### 3. T-IN-14: R2 Storage (6–8h, Day 1–2)
**Status**: Skeleton ready for Builder

Directory: `2026-11-08-t-in-14-r2-storage/`

Production image storage via Cloudflare R2.
- Bucket creation + API tokens
- CORS configuration for frontend access
- Presigned URLs (backend generation, 15-min expiry)
- ImageService integration (S3Client → R2)
- Health check storage endpoint

**Files**:
- `proposal.md` — Problem + scope
- `design.md` — Design decisions (D1–D5)
- `specs/storage/spec.md` — Requirements (RF-STG-01 to RNF-STG-03)
- `tasks.md` — Sections A–G with mutation tests
- `apply-progress.md` — Progress template

**Blocker**: T-IN-13 + ODD #503

---

### 4. T-IN-07r: Railway Backend (6–8h, Day 2–3)
**Status**: Skeleton ready for Builder

Directory: `2026-11-08-t-in-07r-railway-backend/`

Backend refactor to run against Neon + R2 in Railway.
- Remove local DB dependency (Testcontainers remains for tests)
- application-prod.properties with env var overrides
- Boot sequence validation (Liquibase idempotence)
- Railway deployment with injected variables
- Health checks fully operational

**Files**:
- `proposal.md` — Problem + scope
- `design.md` — Design decisions (D1–D5)
- `specs/railway/spec.md` — Requirements (RF-RW-01 to RNF-RW-03)
- `tasks.md` — Sections A–H with mutation tests
- `apply-progress.md` — Progress template

**Blocker**: T-IN-13 + T-IN-14 + ODD #503

---

### 5. T-IN-15: Rate Limiting (4–6h, Day 3.5–4)
**Status**: Skeleton ready for Builder

Directory: `2026-11-08-t-in-15-rate-limiting/`

Protect dashboard endpoints from excessive polling.
- @RateLimited annotations (per-endpoint limits)
- Redis (Upstash serverless) for distributed counting
- Per-user rate limiting (not IP-based)
- Graceful degradation (503 if Redis unavailable)
- 429 HTTP responses + Retry-After headers

**Files**:
- `proposal.md` — Problem + scope
- `design.md` — Design decisions (D1–D5)
- `specs/ratelimit/spec.md` — Requirements (RF-RL-01 to RNF-RL-02)
- `tasks.md` — Sections A–H with mutation tests
- `apply-progress.md` — Progress template

**Blocker**: T-IN-07r (must be live in Railway first)

---

## Execution Timeline

```
ODD #503        T-IN-13         T-IN-14         T-IN-07r        T-IN-15
(4-6h)          (6-8h)          (6-8h)          (6-8h)          (4-6h)
0.5 days        1 day           1 day           1 day           0.5 days
    ↓               ↓               ↓               ↓               ↓
[Mon]-------→[Tue]-------→[Wed]-------→[Thu]-------→[Fri]
                              + Buffer (0.5 days, 10%)
                        TOTAL: 4.5 days
```

---

## File Structure by Task

Each task directory contains **exactly** this structure:

```
2026-11-08-[task-id]-[task-name]/
├── proposal.md              # What + why + criticality
├── design.md                # D1–D5 decisions with alternatives
├── specs/
│   └── [domain]/
│       └── spec.md          # RF-* and RNF-* requirements (Gherkin-style)
├── tasks.md                 # Sections A–H: executable steps + mutations
└── apply-progress.md        # TEMPLATE: fill during execution
```

Total: 5 files per task × 5 tasks = 25 files, 1,874 lines of content.

---

## Builder Instructions

1. **Order is strict**: Execute ODD #503 first. Each subsequent task waits for predecessors.

2. **Per-task workflow**:
   - Read `proposal.md` to understand scope
   - Read `design.md` to understand trade-offs
   - Read `specs/**/spec.md` to know what "done" means
   - Execute sections in `tasks.md` (A, B, C, ..., H)
   - Fill `apply-progress.md` as you work
   - Run mutation tests in section H
   - Verify against "Compuertas" checklist

3. **Mutations**: Every RF is mutated exactly once:
   - Introduce a breaking change
   - Verify the test fails
   - Restore original
   - Delete the test file or revert git
   - Confirm `git status --short` is clean

4. **Branch naming**: Each task creates a branch:
   - `infra/odd-503-domain-setup` (from `develop`)
   - `infra/t-in-13-neon-database` (from `develop`)
   - `infra/t-in-14-r2-storage` (from `develop`)
   - `infra/t-in-07r-railway-backend` (from `develop`)
   - `infra/t-in-15-rate-limiting` (from `develop`)

5. **Commits**: Work-unit commits (~400 lines per commit).
   - No AI co-author (user convention)
   - Use conventional commits (fix: ..., feat: ..., docs: ...)

6. **Archival**: Once complete, move all 5 task directories to:
   ```
   openspec/changes/archive/2026-11-08-infrastructure-mvp/
   ```

---

## Criticality Levels

| Task | Level | Reason |
|------|-------|--------|
| ODD #503 | BLOCKER | Destraba todo; sin DNS + env vars, nada funciona |
| T-IN-13 | CRÍTICO | Sin DB, migraciones no corren; sin schema, app no levanta |
| T-IN-14 | CRÍTICO | Sin storage, evidencias no suben; bloquea T-IN-07r full integration |
| T-IN-07r | CRÍTICO | Conecta todo en Railway; MVP no despliega sin esto |
| T-IN-15 | ALTO | Protege endpoints; no bloquea pero importante para live |

---

## Success Criteria

**All tasks pass if**:
1. Compuertas checklist fully green (curl tests, schema counts, etc.)
2. Mutation tests executed (one per RF)
3. apply-progress.md filled with actual values
4. No hardcoded secrets in code or logs
5. Health checks respond 200/503 appropriately
6. Railway deployment stable (boot time <60s, memory <512 MB)

---

## Related Documentation

- `SDD-PLAYBOOK.md` — Overall methodology
- `documents/ROADMAP.md` — Project roadmap (ODD #490 + SDD #482 archived here)
- `documents/CREDENCIALES.md` — Credentials and access (update per ODD #503)
- `documents/DEPLOYMENT.md` — Deployment procedures (update per ODD #503)

---

## Notes for Builder

- If any task blocks, escalate immediately (don't skip to next task)
- If mutations reveal existing bugs, create a JIRA ticket and note in apply-progress.md
- If Railway build fails, check Railway logs: `railway logs -s backend --follow`
- If DNS doesn't resolve, wait TTL (~5 min) before retrying
- If Neon connection fails, verify IP whitelist and password special characters

---

Generated: 2026-11-08
Format: SDD + ODD methodology
Status: Ready for Builder execution
