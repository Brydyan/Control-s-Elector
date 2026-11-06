# T-COMPLIANCE-07: Design — Data Transparency & Consent

**Date:** 2026-11-05

---

## D1 — Cookie Banner: Always vs First-visit vs Never

**Decisión: First-visit (persist consent in localStorage)**

```
First load → banner aparece
User clicks "Accept All" o "Customize"
localStorage: {consent_given: true, timestamp, preferences: {...}}
Banner no aparece después (stored consent)
```

---

## D2 — Preferences: Simple (All/None) vs Granular

**Decisión: Granular**

```
- [ ] Essential (required)
- [ ] Analytics (Google Analytics, Sentry)
- [ ] Marketing (tracking pixels)
- [ ] Preferences (remember settings)
```

---

**Next:** specs, tasks  
**Last Updated:** 2026-11-05
