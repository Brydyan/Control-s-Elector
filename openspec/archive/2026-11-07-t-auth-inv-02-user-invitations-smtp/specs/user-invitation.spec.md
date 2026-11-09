# Spec: User Invitations + SMTP

## Feature: Invite User with Auto-Generated Username

### Scenario 1: MASTER invites new user with correct username generation

```gherkin
Given MASTER is logged in
And the "Create User" form is visible

When MASTER fills:
  | Field | Value |
  | Nombre | Juan |
  | Apellido | García |
  | Email | juan.garcia@example.com |
  | Rol | OBSERVER |

And MASTER clicks "Enviar Invitación"

Then the API POST /api/v1/users/invite returns 200 OK
And the response body contains:
  | Field | Value |
  | username | juagar.obs@control.ttcs |
  | status | INVITED |
  | emailPersonal | juan.garcia@example.com |

And the DB usuario table contains:
  | username | juagar.obs@control.ttcs |
  | rol | OBSERVER |
  | email | juan.garcia@example.com |

And the DB user_invitations table contains:
  | status | INVITED |
  | email_personal | juan.garcia@example.com |
  | invited_by | [MASTER_ID] |

And an SMTP email is sent to juan.garcia@example.com with:
  | Subject | Contains: "Control Electoral" |
  | Body | Contains: "juagar.obs@control.ttcs" |
  | Body | Contains: "contraseña temporal" |

And the UI shows: "✓ Invitación enviada a juan.garcia@example.com"
```

### Scenario 2: Rate limiting: 1 invite per 5 seconds per IP

```gherkin
Given MASTER sends first invitation
When MASTER immediately sends second invitation within 5 seconds
  (same IP address)

Then the API returns 429 Too Many Requests
And the response header "Retry-After" is set to 5
```

### Scenario 3: Duplicate username collision prevented

```gherkin
Given user "juagar.obs@control.ttcs" already exists

When MASTER tries to invite:
  | Nombre | Juan |
  | Apellido | García |
  | Rol | OBSERVER |

Then the API returns 409 Conflict
And the error message contains: "Usuario juagar.obs@control.ttcs ya existe"
```

### Scenario 4: Invalid email rejected

```gherkin
Given MASTER fills form with:
  | Email | invalid-email |

When MASTER clicks "Enviar Invitación"

Then the form validation shows error: "Email inválido"
And the submit button remains disabled
```

### Scenario 5: Only MASTER can invite users

```gherkin
Given OBSERVER is logged in

When OBSERVER sends POST /api/v1/users/invite

Then the API returns 403 Forbidden
And the error contains: "Solo MASTER puede invitar usuarios"
```

### Scenario 6: Invitation expires after 30 days

```gherkin
Given user invitation created 31 days ago

When the system checks invitation status

Then the invitation status is "EXPIRED"
And the user cannot activate the account with old invitation
```

---

## Acceptance Criteria

- [x] Username format: {3name}{3lastname}.{3role}@control.ttcs
- [x] Temporary password: 12 characters, mixed entropy
- [x] Email validation: RFC 5322 basic check
- [x] Rate limit: 1/5s per IP
- [x] RBAC: Only MASTER
- [x] Audit trail: user_invitations table + audit_log
- [x] SMTP sent: HTML template with credentials
- [x] DB atomicity: Usuario + Invitation in same transaction
- [x] Response: { username, emailPersonal, status }
