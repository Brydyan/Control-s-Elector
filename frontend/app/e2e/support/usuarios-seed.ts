/**
 * Usuarios reales del seed, para los specs que hablan con la API de verdad.
 *
 * <p>La password es la misma para todos: es el hash que siembran las
 * migraciones `025-data-seed.sql` y `026-admin-inicial.sql`.
 *
 * <p>Ojo con el MASTER: `admsismas@control.ttcs` era el admin que creaba el
 * `DataSeeder` del backend Spring. En Quarkus ese seeder ya no existe —lo
 * sustituyo la migracion `026-admin-inicial.sql`— y el master sembrado es
 * `admin@control-elector.local`. Con el correo viejo el login devuelve 401.
 */

export type RolE2E = 'MASTER' | 'OBSERVER' | 'COORDINATOR';

export interface UsuarioSeed {
  email: string;
  password: string;
}

export const USUARIOS_SEED: Record<RolE2E, UsuarioSeed> = {
  MASTER: { email: 'admin@control-elector.local', password: 'admin12345' },
  OBSERVER: { email: 'observervera@example.local', password: 'admin12345' },
  COORDINATOR: {
    email: 'coordinatorcortes@example.local',
    password: 'admin12345',
  },
};
