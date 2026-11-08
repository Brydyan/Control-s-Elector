```yaml
change: "t-fe-11-realtime-dashboard"
date: "2026-10-04"
verifier: "sdd-verify"
mode: "full"
strict_tdd: true

build_evidence:
  tsc:
    command: "npx tsc -p tsconfig.app.json --noEmit"
    exit_code: 0
    output: "No errors"
  lint:
    command: "npx eslint src/app/features/dashboard --ext .ts"
    exit_code: 0
    output: "No errors"
  test_suite:
    command: "./node_modules/.bin/vitest run src/app"
    exit_code: 0
    files: 38
    tests: 220
    failed: 0
    duration: "5.17s"
  test_output_hash: "38passed/220passed/0failed"
  build_output_hash: "tsc:exit0/lint:exit0"

dashboard_tests:
  command: "./node_modules/.bin/vitest run src/app/features/dashboard"
  files: 3
  tests: 20
  failed: 0

coverage:
  command: "./node_modules/.bin/vitest run src/app/features/dashboard --coverage"
  dashboard_page_ts: { stmts: "93.75%", branch: "56.25%", funcs: "86.36%", lines: "93.02%", uncovered: "L55, L126-130" }
  dashboard_realtime_service_ts: { stmts: "87.5%", branch: "100%", funcs: "66.66%", lines: "86.95%", uncovered: "L100, L105, L128" }
  dashboard_service_ts: { stmts: "80%", branch: "100%", funcs: "75%", lines: "78.57%", uncovered: "L51-53" }

completeness:
  total_acceptance_criteria: 6
  covered: 5
  partial: 1

tasks:
  A1: { checked: true, file_exists: true }
  A2: { checked: true, file_exists: true }
  A3: { checked: true, file_exists: true, tests: 7 }
  B1: { checked: true, file_exists: true }
  B2: { checked: true, file_exists: true }
  B3: { checked: true, file_exists: true, tests: 6 }
  C1: { checked: true, file_exists: true, tests: 7 }
  C2: { checked: true, file_exists: true }

verdict: "PASS WITH WARNINGS"
critical: 0
warnings: 5
suggestions: 2
```
