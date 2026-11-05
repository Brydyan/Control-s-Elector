# Specs: T-COMPLIANCE-08 — Backup & Disaster Recovery

**Last Updated:** 2026-11-05

---

## Feature: Automatic Backups

```gherkin
Feature: Backups automáticos y recuperables

  Scenario: Daily backup runs at 23 UTC
    GIVEN cron job scheduled
    WHEN 23:00 UTC
    THEN backup job inicia:
      - Full DB dump to S3
      - File archive (configs, assets)
      - Backup named: backup-${YYYY-MM-DD}.tar.gz
      - Metadata: size, checksum, timestamp

  Scenario: Backup integrity validated
    GIVEN backup completado
    WHEN job termina
    THEN:
      - Calculate checksum (MD5)
      - Store checksum in S3 metadata
      - Verify file readable (not corrupted)
      - Log: "Backup successful, size: XGB"

  Scenario: Backup copied to secondary region 24h after
    GIVEN backup en S3 [primary]
    WHEN 24h pasan
    THEN cross-region copy inicia:
      - Copy backup to S3 [secondary region]
      - Verify copy successful
      - Log: "Cross-region backup complete"

  Scenario: Backups older than 30 días deleted
    GIVEN backup-2026-10-01.tar.gz (31+ días viejo)
    WHEN retention job runs
    THEN delete from S3 (auto-cleanup)
    AND log: "Deleted backup-2026-10-01"

  Scenario: Recovery playbook tested quarterly
    GIVEN recovery procedure documentado
    WHEN Q1, Q2, Q3, Q4
    THEN ops team:
      - Restore from backup to staging
      - Run smoke tests (login, data access, exports)
      - Verify RTO < 4h, RPO < 24h
      - Document: "Recovery test PASS"
      - Notify team: "Recovery capability verified"
```

---

**Total Scenarios:** 5  
**Last Updated:** 2026-11-05
