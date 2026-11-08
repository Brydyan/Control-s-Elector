# Spec: GDPR Data Export Endpoint

## Feature: Data Subject Access Request (Art. 15)

### Scenario 1: Authenticated user exports personal data

```gherkin
Given a logged-in user (userId=123, email=observer@example.com)
When the user calls GET /api/v1/user/export-data

Then the response is:
  - HTTP 200 OK
  - Content-Type: application/json
  - Body:
  {
    "exported_at": "2026-11-06T10:30:45Z",
    "export_request_id": "uuid-abc123",
    "user": {
      "id": 123,
      "email": "observer@example.com",
      "rol": "OBSERVER",
      "first_name": "Juan",
      "last_name": "García",
      "created_at": "2026-11-01T00:00:00Z",
      "accepted_terms_at": "2026-11-05T14:00:00Z",
      "accepted_terms_version": "1.0"
    },
    "actas_registradas": [
      {
        "id": 1,
        "jrv_id": 101,
        "cargo_id": 10,
        "estado": "ENVIADA",
        "created_at": "2026-11-05T10:30:00Z"
      }
    ],
    "dispute_submissions": [],
    "exports_data": [
      {
        "export_id": "uuid-xyz",
        "endpoint": "/api/data/export",
        "exported_at": "2026-11-05T15:00:00Z",
        "record_count": 42
      }
    ]
  }
```

### Scenario 2: Unauthenticated user cannot export

```gherkin
Given an unauthenticated user
When GET /api/v1/user/export-data

Then:
  - HTTP 401 Unauthorized
  - No data exported
```

### Scenario 3: User can only export own data

```gherkin
Given user userId=123 is authenticated
When GET /api/v1/user/export-data?userId=456

Then:
  - HTTP 403 Forbidden (or 401)
  - User cannot see userId=456 data
```

### Scenario 4: Rate limit: 1 export per 24 hours

```gherkin
Given user userId=123 just exported (at 10:00 AM)
When the user tries to export again (at 10:05 AM)

Then:
  - HTTP 429 Too Many Requests
  - Response header: Retry-After: 86300 (seconds until next allowed export)
  - Message: "Max 1 export per 24 hours. Retry after 23:55:00"
```

### Scenario 5: Export history tracked

```gherkin
Given a user calls GET /api/v1/user/export-data
When the request succeeds

Then:
  - Backend logs: INSERT into user_export_log(user_id, exported_at, export_id)
  - Export ID is UUID (trackable)
  - User can see export history in response["exports_data"]
```

### Scenario 6: Export includes only data user has access to

```gherkin
Given user is OBSERVER role
When export includes actas, disputes, nothing coordinator-only

Then:
  - Export ONLY contains actas_registradas and dispute_submissions
  - No metadata about other observers
  - Role-based filtering applied
```

---

## Feature: DPO Contact Form

### Scenario 1: User submits DPO contact form

```gherkin
Given a user navigates to /contact-dpo or modal in /privacy page
When the user submits:
  {
    "name": "Juan García",
    "email": "juan@example.com",
    "subject": "Ejercer derecho al olvido",
    "message": "Quiero borrar mi cuenta..."
  }

Then:
  - HTTP 200 OK (request accepted)
  - Backend records in dpo_contact_requests table
  - User sees confirmation: "Mensaje recibido. DPO responderá en 5 días hábiles"
```

### Scenario 2: Rate limit: 1 contact per IP per 30 seconds

```gherkin
Given an IP address 203.0.113.1
When the user submits DPO contact
And tries to submit again within 30 seconds

Then:
  - HTTP 429 Too Many Requests
  - Message: "Max 1 request per 30 seconds"
```

### Scenario 3: DPO request stored for manual review

```gherkin
Given DPO contact form submitted
When the request is stored

Then:
  - Row in dpo_contact_requests:
    (id=1, name="Juan", email="juan@...", subject="...", submitted_at=now, ip_address="203.0.113.1", status="NEW")
  - DPO can see requests in admin panel (or via query)
  - DPO manually responds (outside system)
```

---

## Acceptance Criteria

- [x] Endpoint GET /api/v1/user/export-data requires authentication
- [x] User can only export own data (not others')
- [x] Response includes user + actas + disputes + export history
- [x] Response is valid JSON (machine-readable)
- [x] Rate limit: 1 export per user per 24 hours
- [x] Export requests logged (audit trail)
- [x] DPO contact form: POST /api/v1/legal/contact-dpo
- [x] DPO requests stored in dpo_contact_requests table
- [x] DPO rate limit: 1 request per IP per 30 seconds
- [x] User cannot bypass rate limit (no client-side only)
