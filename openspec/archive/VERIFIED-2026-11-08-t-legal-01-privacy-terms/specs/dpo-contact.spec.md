# Spec: DPO Contact Form

## Feature: Data Protection Officer Request Submission

### Scenario 1: User submits DPO contact form

```gherkin
Given a user navigates to /contact-dpo or finds form in /privacy page
When the user fills out:
  - Name: "Juan García"
  - Email: "juan@example.com"
  - Subject: "Ejercer derecho al olvido"
  - Message: "Quiero borrar mi cuenta y todos mis datos..."

And clicks "Enviar"

Then the browser:
  - POSTs to /api/v1/legal/contact-dpo
  - Request includes IP address (backend extracts from X-Forwarded-For or socket)
  - Receives response: HTTP 200 OK

And the response is:
  {
    "status": "received",
    "message": "Mensaje recibido. DPO responderá en 5 días hábiles"
  }

And the browser shows confirmation message to user
```

### Scenario 2: DPO request is stored for manual review

```gherkin
Given DPO contact form was submitted
When the DPO team accesses the database table dpo_contact_requests

Then they see row:
  - id: 1
  - name: "Juan García"
  - email: "juan@example.com"
  - subject: "Ejercer derecho al olvido"
  - message: "Quiero borrar mi cuenta..."
  - submitted_at: 2026-11-06 10:30:00
  - ip_address: "203.0.113.1"
  - status: "NEW"
  - dpo_response: null
  - responded_at: null
```

### Scenario 3: Rate limit prevents spam

```gherkin
Given an IP address 203.0.113.1
When the user submits DPO contact at 10:00:00
And tries to submit another request at 10:00:15 (same IP)

Then the second request:
  - Returns HTTP 429 Too Many Requests
  - Response: { "error": "Max 1 request per 30 seconds. Retry after 15 seconds." }
  - Retry-After header: "15"
  - No record added to dpo_contact_requests table
```

### Scenario 4: Rate limit is per IP (not per user)

```gherkin
Given IP address 203.0.113.1 just submitted DPO request
When another IP address 203.0.113.2 submits immediately

Then:
  - IP 203.0.113.2 receives 200 OK (no rate limit)
  - Request is recorded in database
  - IP 203.0.113.1 must wait 30 seconds
```

### Scenario 5: DPO responds to request

```gherkin
Given a DPO request is in status="NEW"
When the DPO team:
  - Reads the request
  - Prepares a response (outside the system)
  - Updates the database: status="IN_PROGRESS", then status="RESOLVED"
  - Sets dpo_response="Hemos iniciado el proceso de eliminación..."
  - Sets responded_at=2026-11-09 14:00:00

Then:
  - The request row is updated
  - History is preserved (submitted_at still shows original submission date)
  - DPO can query: SELECT * FROM dpo_contact_requests WHERE status="NEW" (shows pending)
```

### Scenario 6: Form validation

```gherkin
Given the DPO contact form
When the user tries to submit with:
  - Name: empty
  - Email: "invalid-email"
  - Subject: empty
  - Message: ""

Then the frontend validates BEFORE sending POST:
  - Shows error: "Nombre es requerido"
  - Shows error: "Email debe ser válido"
  - Shows error: "Asunto es requerido"
  - Does NOT submit (POST not sent)

If frontend validation passes but backend rejects:
  - Backend validates again (defense in depth)
  - Returns 400 Bad Request: { "error": "Email must be valid" }
```

---

## Acceptance Criteria

- [x] POST /api/v1/legal/contact-dpo accepts form data
- [x] Stores request in dpo_contact_requests table
- [x] Rate limit: 1 request per IP per 30 seconds
- [x] Returns 200 on success, 429 on rate limit
- [x] IP address captured (X-Forwarded-For or socket)
- [x] Frontend form validation (name, email, subject, message)
- [x] Backend form validation (email regex, message length)
- [x] Status field allows DPO to track (NEW → IN_PROGRESS → RESOLVED)
- [x] DPO can manually update response + responded_at timestamp
