import { definePreset } from '@primeuix/themes';
import Aura from '@primeuix/themes/aura';

/**
 * Institutional design tokens for PrimeNG.
 *
 * Source of truth: `src/theme/tokens.scss`. The values below are a literal
 * copy of the CSS custom properties declared there, kept as plain hex so the
 * preset can be asserted in unit tests without a CSS-variable runtime.
 *
 * S6 of `odd/tasks/frontend-refactor-primeng-sakai.md`: the institutional
 * palette must survive the component-library migration. Aura (the Sakai
 * default) ships an emerald primary; this preset swaps it for the
 * institutional blue.
 */

/** `--primitive-blue-*` from tokens.scss, with the ramp gaps filled from the same family. */
const BRAND_BLUE = {
  50: '#eff6ff', // --primitive-blue-50
  100: '#dbeafe', // --primitive-blue-100
  200: '#bfdbfe', // --primitive-blue-200
  300: '#93c5fd',
  400: '#60a5fa',
  500: '#2563eb', // --primitive-blue-500 -> brand primary
  600: '#1d4ed8', // --primitive-blue-600 -> brand primary strong
  700: '#1e40af', // --primitive-blue-700
  800: '#1e3a8a', // --primitive-blue-900
  900: '#172554',
  950: '#0f172a', // --primitive-slate-900 -> deepest institutional neutral
} as const;

/** `--radius-*` from tokens.scss, mapped onto PrimeUI's borderRadius stops. */
const BRAND_RADIUS = {
  none: '0',
  xs: '4px',
  sm: '6px', // --radius-sm
  md: '8px', // --radius-md
  lg: '12px', // --radius-lg
  xl: '16px', // --radius-xl
} as const;

/**
 * Dark scheme surfaces, pinned to the institutional slate ramp.
 *
 * Aura uses the slate ramp for the light scheme but switches to the neutral
 * zinc ramp for the dark one. The design system declares a single neutral
 * ramp (`--primitive-slate-*`), and the shell paints the sidebar with
 * `--color-surface-sidebar` (`slate-900`), so the dark scheme follows the
 * same ramp instead of introducing a second one.
 */
const BRAND_DARK_SURFACE = {
  0: '#ffffff',
  50: '{slate.50}',
  100: '{slate.100}',
  200: '{slate.200}',
  300: '{slate.300}',
  400: '{slate.400}',
  500: '{slate.500}',
  600: '{slate.600}',
  700: '{slate.700}',
  800: '{slate.800}',
  900: '{slate.900}',
  950: '{slate.950}',
} as const;

/**
 * The face of the app, declared for the component library.
 *
 * Source of truth: `src/theme/tokens.scss`. The values below are a literal
 * copy of the CSS custom properties declared there, kept as plain values so
 * the preset can be asserted in unit tests without a CSS-variable runtime.
 *
 * S9 of `odd/tasks/frontend-design-system-hardening.md`: the app had no
 * typeface at all, so everything rendered in the user agent's default serif.
 * PrimeNG's components inherit the family from the body
 * (`font-family: inherit`), and this token is what the theme publishes as
 * `--p-font-family`: the chrome and the content are the same voice by
 * declaration, not by accident.
 */
export const APP_FONT_FAMILY =
  '"IBM Plex Sans", "IBM Plex Sans Fallback", Arial, "Helvetica Neue", system-ui, sans-serif';

/**
 * PrimeNG preset derived from Aura.
 *
 * Only the layers the institutional identity depends on are overridden: the
 * primary ramp (brand), the blue primitive it references, the radius scale,
 * the dark surfaces and the family. Everything else (component tokens, light
 * surfaces) is inherited from Aura and stays themeable through the same token
 * mechanism.
 */
export const electoralPreset = definePreset(Aura, {
  primitive: {
    blue: BRAND_BLUE,
    borderRadius: BRAND_RADIUS,
  },
  semantic: {
    fontFamily: APP_FONT_FAMILY,
    primary: {
      50: BRAND_BLUE[50],
      100: BRAND_BLUE[100],
      200: BRAND_BLUE[200],
      300: BRAND_BLUE[300],
      400: BRAND_BLUE[400],
      500: BRAND_BLUE[500],
      600: BRAND_BLUE[600],
      700: BRAND_BLUE[700],
      800: BRAND_BLUE[800],
      900: BRAND_BLUE[900],
      950: BRAND_BLUE[950],
    },
    colorScheme: {
      dark: {
        surface: BRAND_DARK_SURFACE,
      },
    },
  },
});

export { BRAND_BLUE, BRAND_RADIUS, BRAND_DARK_SURFACE };
