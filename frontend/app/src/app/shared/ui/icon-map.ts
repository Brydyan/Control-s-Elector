/**
 * The icon table of the app.
 *
 * There used to be two: this file (sixty Ionic names, the leftovers of the
 * migration) and a switch of eleven backend keys inside the sidebar. They
 * resolved to the same fallback glyph, so the mapping was duplicated with
 * two vocabularies.
 *
 * The table is keyed by **what the backend sends**: `menu.icono` carries a
 * stable key ("home", "document", "people", …), not a library-specific name,
 * so the catalog does not change when the icon library does, and the menu
 * that comes from the API resolves without a translation of its own.
 *
 * PrimeIcons are plain CSS classes, so the target is a full class list such
 * as `pi pi-home`.
 */

/** The glyph a missing or unknown key falls back to. */
export const FALLBACK_ICON = 'pi pi-file';

export const ICON_MAP: Record<string, string> = {
  // ── El catálogo del backend (`menu.icono`): la fuente de verdad ──────────
  home: 'pi pi-home',
  document: 'pi pi-file',
  'add-circle': 'pi pi-plus-circle',
  checkmark: 'pi pi-check-circle',
  image: 'pi pi-image',
  'cloud-upload': 'pi pi-cloud-upload',
  settings: 'pi pi-cog',
  people: 'pi pi-users',
  pulse: 'pi pi-chart-line',

  /*
   * Los nombres de Ionic que todavía escribe `src/app/app.routes.ts` (fuera
   * de la superficie de esta fase). Mapean al mismo glifo que su clave del
   * backend; la fase que toca las rutas los retira.
   */
  'document-text-outline': 'pi pi-file',
  'checkmark-circle-outline': 'pi pi-check-circle',
  'images-outline': 'pi pi-file', // el plural no tiene equivalente: cae al documento
  'cloud-upload-outline': 'pi pi-cloud-upload',
  'people-outline': 'pi pi-users',
  'pulse-outline': 'pi pi-chart-line',
  'ribbon-outline': 'pi pi-bookmark',
  'flag-outline': 'pi pi-flag',
  'person-outline': 'pi pi-user',
};

/**
 * Translates an icon key into its PrimeIcons class list.
 *
 * Unknown names fall back to a document icon so a missing entry never leaves
 * a blank space in the interface.
 */
export function toPrimeIcon(name?: string | null): string {
  if (!name) return FALLBACK_ICON;
  return ICON_MAP[name] ?? FALLBACK_ICON;
}
