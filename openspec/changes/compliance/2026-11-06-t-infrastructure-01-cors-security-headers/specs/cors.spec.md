# Spec: CORS Configuration & Preflight Handling

## Feature: Cross-Origin Resource Sharing

### Scenario 1: Preflight OPTIONS for POST /api/auth/login

```gherkin
Given an unauthenticated browser at https://control.twintailcs.xyz
When the browser sends an OPTIONS preflight request to https://api.twintailcs.xyz/api/auth/login
  With headers:
    - Origin: https://control.twintailcs.xyz
    - Access-Control-Request-Method: POST
    - Access-Control-Request-Headers: Content-Type, Authorization

Then the server responds with status 200
And the response includes headers:
  - Access-Control-Allow-Origin: https://control.twintailcs.xyz
  - Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS
  - Access-Control-Allow-Headers: Content-Type, Authorization, X-Requested-With, Accept
  - Access-Control-Allow-Credentials: true
  - Access-Control-Max-Age: 86400
```

### Scenario 2: Actual POST /api/auth/login after preflight

```gherkin
Given a preflight OPTIONS request succeeded
When the browser sends a POST request to https://api.twintailcs.xyz/api/auth/login
  With credentials: 'include' (sends cookies)
  With headers:
    - Content-Type: application/json
    - Authorization: Bearer <token-if-exists>

Then the server processes the request (no 401 from preflight)
And the response includes:
  - Access-Control-Allow-Origin: https://control.twintailcs.xyz
  - Access-Control-Allow-Credentials: true
  - Set-Cookie: refresh_token=<value>; HttpOnly; Secure; SameSite=Strict; Path=/api/auth/refresh
```

### Scenario 3: Rejected origin (not in whitelist)

```gherkin
Given a browser at https://evil-site.com
When the browser sends a preflight OPTIONS to https://api.twintailcs.xyz/api/auth/login
  With Origin: https://evil-site.com

Then the server responds with status 200 (preflight always 200)
But the response DOES NOT include Access-Control-Allow-Origin header
And the browser blocks the fetch (SOP violation)
```

### Scenario 4: Development environment: localhost origins

```gherkin
Given dev environment with quarkus.http.cors.origins=http://localhost:4200,http://localhost:4300
When a browser at http://localhost:4200 sends OPTIONS preflight
  With Origin: http://localhost:4200

Then the server responds with:
  - Access-Control-Allow-Origin: http://localhost:4200
  - Access-Control-Max-Age: 3600 (1 hour in dev)
```

### Scenario 5: Refresh token endpoint restricts origin

```gherkin
Given a POST request to https://api.twintailcs.xyz/api/auth/refresh
When the request origin is https://evil-site.com
  (credentials: 'include' sent, browser tried to use refresh cookie)

Then the preflight fails (no Access-Control-Allow-Origin for evil-site.com)
And the browser does not send the refresh_token cookie
```

---

## Acceptance Criteria

- [x] OPTIONS requests return 200 OK (not 401 Unauthorized)
- [x] CORS headers present in all responses from allowed origins
- [x] Wildcard `*` origin NEVER used
- [x] SameSite=Strict on refresh_token cookie
- [x] HttpOnly flag prevents JS access to refresh_token
- [x] Preflight responses are cached (max-age header set)
- [x] Rejected origins don't leak error messages
