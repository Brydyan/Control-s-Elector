import { electoralPreset } from './primeng-preset';

/**
 * Class applied to `document.documentElement` to activate the dark scheme.
 * Matches the Sakai template convention and is the single switch the theme
 * service toggles.
 */
export const DARK_MODE_CLASS = 'app-dark';

/**
 * PrimeNG theme configuration.
 *
 * `darkModeSelector` replaces PrimeNG's default `system` media query with an
 * explicit class, so the theme state is owned by the app instead of the OS.
 */
export const primengTheme = {
  preset: electoralPreset,
  options: {
    prefix: 'p',
    darkModeSelector: `.${DARK_MODE_CLASS}`,
    cssLayer: false,
  },
};
