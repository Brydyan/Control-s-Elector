# Proposal — T-MONITORING-01: Sentry Error Tracking & Monitoring

**Date:** 2026-11-06  
**Priority:** CRITICAL  
**Estimación:** 4-5 puntos → 1 sprint

---

## Qué se construye

Centralizado error tracking y crash reporting via Sentry con privacy controls:
- **Backend integration:** Sentry SDK para Quarkus, captura exceptions, logs críticos, eventos custom (login, data access)
- **Frontend integration:** Sentry Angular SDK, captura JS errors, console warnings, custom events
- **PII scrubbing:** Redacción automática de emails, passwords, tokens, IDs en stack traces y breadcrumbs
- **Custom events:** login attempts (success/failure), data access (actas, resultados), exports
- **Alerting:** Slack webhook para CRITICAL errors, resumen diario vía email
- **Rate limiting:** sampling 10% errors en producción, 100% en dev/staging (evitar spam)
- **Dashboard:** Sentry dashboard con filtros por entorno, error type, user, host

## Por qué es necesario

**Hoy invisible en producción:**
- ❌ Backend crashes: sin logs centralizados, Ops no se enteras hasta que usuario reporta
- ❌ Frontend JS errors: silenciosos, usuario ve pantalla en blanco, no hay traza
- ❌ Debugging: stack traces en logs distribuidos, nadie las recopila ni agrupa
- ❌ Compliance: electoral commission exige auditoría de errores críticos (login failures, data access anomalías)

**Con esto:**
- ✅ Crash reports → automáticas a Sentry en <1s, sin cargar al usuario
- ✅ PII redactada → cumple GDPR, no se capturan passwords/tokens
- ✅ Alertas Slack → ops se entera en tiempo real de CRITICAL errors
- ✅ Auditoría: Sentry es logging de eventos críticos con timestamps, user context, stack traces
- ✅ Debugging: Slack + email alert → dev puede investigar en Sentry dashboard

## Problema específico

1. **Zero Sentry integration:** No existe `io.sentry:sentry-quarkus` en pom.xml, no hay `main.ts` Sentry init en frontend
2. **No PII filtering:** Si se implementara hoy, capturaría passwords, tokens, emails en clarito
3. **No custom events:** No hay tracking de login attempts, data access, exports (auditoría compliance lost)
4. **No alerting:** Errors quedan en Sentry, pero nobody notifies ops team; lunes descubren que viernes falló todo

## Solución alto nivel

```
[Error event in app]
    ↓
[Sentry SDK: capture exception/message]
    ↓
[PII processor: redact emails, passwords, tokens]
    ↓
[Sampling: 10% prod, 100% dev]
    ↓
[Sentry DSN: HTTP POST to https://sentry.io/...]
    ↓
[Sentry: store event, group by fingerprint]
    ↓
[Alert trigger: if severity=CRITICAL]
    ├─ [Slack webhook: #ops-alerts]
    └─ [Email daily digest]
```

**Backend stack:**
- **pom.xml:** add `io.sentry:sentry-quarkus`, `io.sentry:sentry-logback` (intercept logs)
- **application.properties:**
  - `quarkus.sentry.dsn` = `https://...@sentry.io/...` (create project in Sentry.io)
  - `quarkus.sentry.environment` = `dev` / `staging` / `prod`
  - `quarkus.sentry.sample-rate` = `1.0` (dev), `0.1` (prod)
  - `quarkus.sentry.traces-sample-rate` = `0.1` (performance monitoring, low volume)
  - `quarkus.sentry.logging.minimum-event-level` = `WARN` (send WARN+ to Sentry)
- **Custom events:** `SentryService` bean
  ```java
  @ApplicationScoped
  public class SentryService {
    public void trackLogin(String userId, boolean success) {
      SentryEvent event = new SentryEvent();
      event.setLevel(success ? SentryLevel.INFO : SentryLevel.WARNING);
      event.setMessage("login " + (success ? "success" : "failure"));
      event.setTag("event_type", "auth");
      event.setTag("user_id", userId);
      Sentry.captureEvent(event);
    }
  }
  ```
- **Logback integration:** Sentry appender intercepts logs, sends ERROR+ to Sentry

**Frontend:**
- **main.ts:** initialize Sentry
  ```typescript
  import * as Sentry from "@sentry/angular";
  
  Sentry.init({
    dsn: environment.sentryDsn,
    environment: environment.production ? 'prod' : 'dev',
    sampleRate: environment.production ? 0.1 : 1.0,
    denyUrls: [/localhost/],
    beforeSend: (event) => {
      // PII redaction
      if (event.request?.headers) {
        delete event.request.headers['authorization'];
      }
      return event;
    }
  });
  ```
- **Custom events:**
  ```typescript
  Sentry.captureMessage('login_success', 'info', { tags: { event_type: 'auth' }});
  ```
- **Slack integration:** Sentry project → Settings → Integrations → Slack → add #ops-alerts

## Alcance

✅ DENTRO:
- Sentry project creation and DSN setup
- Quarkus backend SDK integration (sentry-quarkus + sentry-logback)
- Angular frontend SDK integration (main.ts Sentry.init)
- PII scrubbing: auto-redact emails, passwords, Authorization headers, tokens
- Custom events: login attempts (success/failure), data access (actas/resultados/exports), errors
- Rate limiting: 100% sampling dev/staging, 10% sampling prod
- Slack alerting: CRITICAL errors → #ops-alerts channel with link to Sentry
- Email daily digest: summary of errors/crashes per environment
- Sentry dashboard: filters by environment, error type, user, host, time range
- Tests: unit tests para SentryService, mock Sentry client; integration tests with sentry-testkit
- Configuration: `application.properties` con DSN, sample rates, min log level por entorno
- Documentation: how to view errors in Sentry dashboard, how to set up Slack integration

❌ FUERA:
- Custom Sentry Self-Hosted server → futuro (use Sentry.io SaaS)
- APM (Application Performance Monitoring, transactions tracing) → futuro
- Session replay (Sentry Plus feature) → futuro / privacy concerns
- Custom alerting rules (PagerDuty, Opsgenie) → futuro, Slack sufficient
- Metrics monitoring (custom gauges/histograms) → futuro, use Prometheus if needed
- Error budget / SLOs → futuro

---

## Beneficios

| Aspecto | Valor |
|---|---|
| **Observability** | Production errors visible sin esperar a user report. Stack traces completos. |
| **Incident Response** | Slack alert → ops aware instantly. Can investigate in Sentry before email. |
| **Debugging** | Grouping by fingerprint → "this error happened 50 times this week, started 3 days ago". |
| **Compliance** | Audit log: login attempts, data access, exports con timestamps, user context. |
| **PII Safety** | Auto-redaction → no passwords/tokens leaked to Sentry. GDPR compliant. |
| **Cost** | Sentry.io free tier: 5k events/month. Paid tier: $29/month. Electoral scale << 50k events/month. |
| **Confidence** | Known unknowns: error patterns visible. Can proactively fix issues. |

---

## Criterios de aceptación

- [ ] Sentry project created in Sentry.io, DSN copied to `application.properties`
- [ ] `pom.xml` includes `io.sentry:sentry-quarkus` and `io.sentry:sentry-logback`
- [ ] Backend: `application.properties` configured with Sentry DSN, environment, sample rates
- [ ] Backend: `SentryService` bean tracks login attempts (success/failure)
- [ ] Backend: `SentryService` tracks data access events (actas, resultados, exports)
- [ ] Backend: Error logs (WARN+) automatically sent to Sentry via Logback appender
- [ ] Frontend: `main.ts` initializes Sentry Angular SDK with DSN
- [ ] Frontend: sample rate 100% dev/staging, 10% prod (configurable per environment)
- [ ] Frontend: custom event tracking for login, navigation, critical user actions
- [ ] PII scrubbing: email addresses redacted in stack traces and breadcrumbs
- [ ] PII scrubbing: passwords and Authorization headers removed before sending to Sentry
- [ ] Slack integration: #ops-alerts receives CRITICAL error notifications with Sentry link
- [ ] Email daily digest: summary of errors grouped by environment and severity
- [ ] Unit tests: `SentryService` sends events correctly (mocked Sentry client)
- [ ] Integration tests: with sentry-testkit, verify captured events match expectations
- [ ] Documentation: Sentry dashboard overview, how to filter errors, how to set up alerts
- [ ] Documentation: PII policy explanation, why auto-redaction matters for compliance

---

## Riesgos

| Riesgo | Probabilidad | Impacto | Mitigación |
|---|---|---|---|
| **PII accidentally captured** | Baja (with redaction rules) | Alto (GDPR violation) | Review Sentry events manually first month; add more redaction rules if needed. |
| **Sentry quota exceeded** | Baja (10% sampling, <50k events/month typical) | Medio (events dropped) | Monitor quota in Sentry dashboard; adjust sample rate if needed. |
| **Slack integration misconfigured** | Baja | Medio (alerts not delivered) | Test webhook manually; verify channel permissions. |
| **Performance overhead** | Baja (<1ms per event) | Low | Acceptable; verify with load testing. |
| **Noisy alerts (too many CRITICAL)** | Media (if severity rules not tuned) | Medium (alert fatigue) | Tune fingerprinting rules; group similar errors; adjust severity levels. |
| **Sentry.io SaaS outage** | Muy baja | Medium (errors not sent) | Fallback: continue logging to local files; Sentry backfill when recovered. |

---

**Status:** Ready for design  
**Last Updated:** 2026-11-06
