/**
 * Domain model for the authenticated user.
 *
 * This is the shape the rest of the app consumes. It is deliberately
 * decoupled from the transport DTO (see data/auth.dto.ts) so that a
 * change in the API contract does not ripple through the whole app.
 */

export type Role = 'MASTER' | 'COORDINATOR' | 'ADMIN' | 'OBSERVER';

export interface CurrentUser {
  id: string;
  email: string;
  role: Role;
  permissions: string[];
  acceptedTermsVersion?: string | null;
  acceptedPrivacyVersion?: string | null;
}
