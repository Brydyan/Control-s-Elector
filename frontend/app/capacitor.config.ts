import type { CapacitorConfig } from '@capacitor/cli';

/**
 * The application id identifies the Android package and the deep-link
 * scheme. It used to be the Ionic starter placeholder (`io.ionic.starter`),
 * which would have published the app under someone else's namespace.
 */
const APP_ID = 'ec.cne.controlelectoral';

const config: CapacitorConfig = {
  appId: APP_ID,
  appName: 'Control Electoral',
  webDir: 'www',
};

export default config;
