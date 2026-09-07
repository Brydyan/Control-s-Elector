# Design — T-IN-11: GitHub Actions CI/CD

## D1 — Trigger CD: main only

**Decisión:** CD solo en `main` branch (no develop). Develop puede tener PRs con CI tests.

**Alternativa descartada — CD en develop también.** Menos control, staging menos estable.

## D2 — Tests: Maven + ng test

**Decisión:** Backend: `mvn test`, Frontend: `ng test --watch=false --browsers=ChromeHeadless`.

**Alternativa descartada — ng test --browsers=Chrome.** Headless es necesario en CI (sin GUI).

## D3 — Secrets en GitHub

**Decisión:** Railway token + Vercel token en GitHub Secrets.

**Alternativa descartada — hardcode.** Inseguro, nunca.

## D4 — Parallelizar o secuencial

**Decisión:** Parallelizar backend test + frontend test (más rápido). CD después (ambos deben pasar).

**Alternativa descartada — secuencial.** Más lento.
